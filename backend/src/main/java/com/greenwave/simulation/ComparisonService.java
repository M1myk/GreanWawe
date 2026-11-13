package com.greenwave.simulation;

import com.greenwave.network.RoadNetworkReader;
import com.greenwave.network.RoadRoute;
import com.greenwave.scenario.persistence.ScenarioRepository;
import com.greenwave.shared.api.ApiException;
import com.greenwave.trip.api.dto.TripConfigRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import static com.greenwave.simulation.ComparisonSnapshot.Status;

@Service
public class ComparisonService {
    private static final Set<Double> RATES = Set.of(0.5, 1.0, 2.0, 5.0, 10.0);
    private static final long SESSION_IDLE_NANOS = Duration.ofMinutes(30).toNanos();
    private static final int CAPACITY = 32;
    private static final int COMMAND_HISTORY_LIMIT = 128;
    private final Map<String, ComparisonSession> sessions = new HashMap<>();
    private final RoadNetworkReader networks;
    private final ScenarioRepository scenarios;
    private final ComparisonStore store;
    private final SavedTimeExamples examples;

    public ComparisonService(RoadNetworkReader networks, ScenarioRepository scenarios, ComparisonStore store,
                             SavedTimeExamples examples) {
        this.networks = networks;
        this.scenarios = scenarios;
        this.store = store;
        this.examples = examples;
    }

    ComparisonSnapshot create(TripConfigRequest config) {
        var session = new ComparisonSession(UUID.randomUUID().toString(), definition(config), Instant.now().toString());
        synchronized (this) {
            requireCapacity();
            var snapshot = session.snapshot();
            store.save(snapshot);
            sessions.put(session.id, session);
            return snapshot;
        }
    }

    synchronized ComparisonSnapshot get(String id) {
        var session = sessions.get(id);
        if (session == null) return stored(id);
        session.lastAccess = System.nanoTime();
        return session.snapshot();
    }

    synchronized ComparisonSnapshot result(String id) {
        var snapshot = get(id);
        if (!snapshot.terminal()) throw conflict("RESULT_NOT_READY", "The comparison is still in progress.");
        return snapshot;
    }

    synchronized void release(String id) {
        if (!sessions.containsKey(id)) stored(id);
        store.discardUnfinished(id);
        sessions.remove(id);
    }

    synchronized ComparisonSnapshot command(String id, ComparisonController.Command request) {
        var session = sessions.get(id);
        if (session == null) {
            var snapshot = stored(id);
            if (request.type() != SimulationController.CommandType.RESET) {
                throw conflict("INVALID_COMPARISON_STATE", "Only RESET can reopen a stored comparison.");
            }
            requireCapacity();
            session = new ComparisonSession(id, definition(snapshot.config()), snapshot.createdAt());
            session.revision = session.commandRevision = snapshot.revision();
            session.rate = snapshot.playbackRate();
        }
        session.lastAccess = System.nanoTime();
        var replay = session.commands.get(request.commandId());
        if (replay != null) {
            if (!replay.command().equals(request)) throw conflict("COMMAND_CONFLICT", "Command ID has different content.");
            return replay.snapshot();
        }
        if (request.expectedRevision() < session.commandRevision || request.expectedRevision() > session.revision) {
            throw conflict("REVISION_CONFLICT", "The comparison changed. Reload it before sending another command.");
        }
        if (request.type() != SimulationController.CommandType.SET_PLAYBACK_RATE && request.playbackRate() != null) {
            throw ApiException.invalid("playbackRate", "Only SET_PLAYBACK_RATE accepts a rate.");
        }
        if (request.type() == SimulationController.CommandType.RESET) {
            var replacement = new ComparisonSession(id, session.definition, session.createdAt);
            replacement.revision = session.revision + 1;
            replacement.rate = session.rate;
            replacement.commands.putAll(session.commands);
            store.save(replacement.snapshot());
            sessions.put(id, replacement);
            session = replacement;
        } else {
            apply(session, request);
            session.revision++;
        }
        session.commandRevision = session.revision;
        var snapshot = session.snapshot();
        if (session.commands.size() == COMMAND_HISTORY_LIMIT) {
            session.commands.remove(session.commands.keySet().iterator().next());
        }
        session.commands.put(request.commandId(), new ComparisonSession.Applied(request, snapshot));
        return snapshot;
    }

    private void apply(ComparisonSession session, ComparisonController.Command request) {
        switch (request.type()) {
            case START -> transition(session, Status.READY, Status.RUNNING, BaselineSimulation.Status.READY, BaselineSimulation.Status.RUNNING);
            case PAUSE -> transition(session, Status.RUNNING, Status.PAUSED, BaselineSimulation.Status.RUNNING, BaselineSimulation.Status.PAUSED);
            case RESUME -> transition(session, Status.PAUSED, Status.RUNNING, BaselineSimulation.Status.PAUSED, BaselineSimulation.Status.RUNNING);
            case SET_PLAYBACK_RATE -> {
                if (request.playbackRate() == null || !RATES.contains(request.playbackRate())) {
                    throw ApiException.invalid("playbackRate", "Use 0.5, 1, 2, 5 or 10.");
                }
                if (session.status() == Status.COMPLETED || session.status() == Status.FAILED) {
                    throw conflict("INVALID_COMPARISON_STATE", "Reset the comparison to change playback rate.");
                }
                session.rate = request.playbackRate();
            }
            case RESET -> throw new IllegalStateException("RESET is handled before applying commands");
        }
    }

    @Scheduled(fixedRate = 100)
    synchronized void tick() {
        long now = System.nanoTime();
        var iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            var session = iterator.next();
            try {
                if (now - session.lastAccess >= SESSION_IDLE_NANOS) {
                    store.discardUnfinished(session.id);
                    iterator.remove();
                    continue;
                }
                if (session.status() != Status.RUNNING) continue;
                try {
                    session.pulse();
                } catch (RuntimeException exception) {
                    LoggerFactory.getLogger(getClass()).error("Comparison tick failed", exception);
                    session.fail("ENGINE_FAILURE", "The comparison engine could not advance.");
                }
                switch (session.status()) {
                    case RUNNING -> prefetchExample(session);
                    case COMPLETED -> session.savedTimeExample = store.saveCompleted(session.snapshot(), session.exampleDraft);
                    default -> store.save(session.snapshot());
                }
            } catch (RuntimeException exception) {
                LoggerFactory.getLogger(getClass()).error("Comparison storage failed", exception);
                session.fail("STORAGE_FAILURE", "The comparison result could not be saved.");
            }
        }
    }

    private void prefetchExample(ComparisonSession session) {
        if (!examples.prefetchEnabled()) return;
        int estimate = session.takeExampleEstimate();
        if (estimate == 0) return;
        try {
            session.exampleDraft = examples.prefetch(estimate);
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(getClass()).warn("Saved-time example prefetch failed.");
        }
    }

    private ComparisonSession.Definition definition(TripConfigRequest config) {
        var settings = SimulationSettings.from(config);
        var scenario = scenarios.findAll().stream().filter(item -> item.id().equals(config.scenarioId())).findFirst()
                .orElseThrow(() -> ApiException.invalid("scenarioId", "Unknown scenario."));
        var network = networks.get(scenario.networkId());
        if (network.nodes().stream().noneMatch(node -> node.id().equals(config.startNodeId()))) {
            throw ApiException.invalid("startNodeId", "Unknown start node.");
        }
        if (config.startNodeId().equals(config.destinationNodeId())
                || network.nodes().stream().noneMatch(node -> node.id().equals(config.destinationNodeId()))) {
            throw ApiException.invalid("destinationNodeId", "Choose a different destination on the scenario network.");
        }
        var edges = RoadRoute.findEdges(network, config.startNodeId(), config.destinationNodeId(), settings.maxSpeed());
        return new ComparisonSession.Definition(config, network, scenario.version(), settings, SimulationSignals.Scenario.from(network), edges,
                SimulationTraffic.create(network, config.trafficMode(), config.randomSeed()));
    }

    private ComparisonSnapshot stored(String id) {
        return store.find(id).orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Comparison not found."));
    }

    private void requireCapacity() {
        if (sessions.size() >= CAPACITY) throw conflict("SESSION_LIMIT", "Comparison session limit reached.");
    }

    private static void transition(ComparisonSession session, Status expected, Status next,
                                   BaselineSimulation.Status fromVehicle, BaselineSimulation.Status toVehicle) {
        if (session.status() != expected) throw conflict("INVALID_COMPARISON_STATE", "Command requires " + expected + ".");
        session.transition(next, fromVehicle, toVehicle);
    }

    private static ApiException conflict(String code, String message) { return new ApiException(409, code, message); }
}
