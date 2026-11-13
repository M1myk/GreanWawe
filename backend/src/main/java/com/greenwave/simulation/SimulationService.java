package com.greenwave.simulation;

import com.greenwave.network.RoadNetworkReader;
import com.greenwave.network.RoadRoute;
import com.greenwave.shared.api.ApiException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {
    private static final Set<Double> RATES = Set.of(0.5, 1.0, 2.0, 5.0, 10.0);
    private static final long SESSION_IDLE_NANOS = Duration.ofMinutes(30).toNanos();
    private static final int COMMAND_HISTORY_LIMIT = 128;
    private final RoadNetworkReader networks;
    private final Map<String, Session> sessions = new HashMap<>();

    private record Applied(SimulationController.Command request, BaselineSimulation.Snapshot response) {}

    private static final class Session {
        private final BaselineSimulation engine;
        private final Map<String, Applied> commands = new LinkedHashMap<>();
        private double rate = 1;
        private double pendingTicks;
        private long lastAccess = System.nanoTime();

        Session(BaselineSimulation engine) { this.engine = engine; }
        BaselineSimulation.Snapshot snapshot(String id) { return engine.snapshot(id, rate); }
    }

    public SimulationService(RoadNetworkReader networks) {
        this.networks = networks;
    }

    BaselineSimulation.Snapshot create(SimulationController.Create request) {
        if (request.startNodeId().equals(request.destinationNodeId())) {
            throw new ApiException(422, "INVALID_ENDPOINTS", "Start and destination must differ");
        }
        var network = networks.get(request.networkId());
        var edges = RoadRoute.findEdges(network, request.startNodeId(), request.destinationNodeId(), BaselineSimulation.MAX_SPEED);
        var algorithm = request.algorithm() == null ? BaselineSimulation.Algorithm.ASTAR : request.algorithm();
        var scenario = SimulationSignals.Scenario.from(network);
        var session = new Session(new BaselineSimulation(edges, request.randomSeed(), scenario, algorithm, network));
        synchronized (this) {
            if (sessions.size() >= 32) throw new ApiException(409, "SESSION_LIMIT", "Simulation session limit reached");
            String id = UUID.randomUUID().toString();
            sessions.put(id, session);
            return session.snapshot(id);
        }
    }

    synchronized BaselineSimulation.Snapshot get(String id) { return requireSession(id).snapshot(id); }

    synchronized void delete(String id) {
        requireSession(id);
        sessions.remove(id);
    }

    synchronized BaselineSimulation.Snapshot command(String id, SimulationController.Command request) {
        var session = requireSession(id);
        var previous = session.commands.get(request.commandId());
        if (previous != null) {
            if (!previous.request().equals(request)) {
                throw new ApiException(409, "COMMAND_CONFLICT", "Command identifier was used with different content");
            }
            return previous.response();
        }
        if (request.type() != SimulationController.CommandType.SET_PLAYBACK_RATE && request.playbackRate() != null) {
            throw new ApiException(422, "INVALID_COMMAND", "Only SET_PLAYBACK_RATE accepts a rate");
        }
        switch (request.type()) {
            case START -> transition(session, BaselineSimulation.Status.READY, BaselineSimulation.Status.RUNNING);
            case PAUSE -> transition(session, BaselineSimulation.Status.RUNNING, BaselineSimulation.Status.PAUSED);
            case RESUME -> transition(session, BaselineSimulation.Status.PAUSED, BaselineSimulation.Status.RUNNING);
            case RESET -> {
                session.engine.reset();
                session.pendingTicks = 0;
            }
            case SET_PLAYBACK_RATE -> {
                if (request.playbackRate() == null || !RATES.contains(request.playbackRate())) {
                    throw new ApiException(422, "INVALID_PLAYBACK_RATE", "Playback rate must be 0.5, 1, 2, 5 or 10");
                }
                session.rate = request.playbackRate();
            }
        }
        var response = session.snapshot(id);
        if (session.commands.size() == COMMAND_HISTORY_LIMIT) {
            session.commands.remove(session.commands.keySet().iterator().next());
        }
        session.commands.put(request.commandId(), new Applied(request, response));
        return response;
    }

    @Scheduled(fixedRate = 100)
    synchronized void tick() {
        long now = System.nanoTime();
        sessions.values().removeIf(session -> now - session.lastAccess >= SESSION_IDLE_NANOS);
        for (var session : sessions.values()) {
            if (session.engine.status() != BaselineSimulation.Status.RUNNING) continue;
            session.pendingTicks += session.rate;
            while (session.pendingTicks >= 1 && session.engine.status() == BaselineSimulation.Status.RUNNING) {
                session.pendingTicks--;
                session.engine.advance();
            }
        }
    }

    private Session requireSession(String id) {
        var session = sessions.get(id);
        if (session == null) throw new ApiException(404, "NOT_FOUND", "Simulation not found");
        session.lastAccess = System.nanoTime();
        return session;
    }

    private static void transition(Session session, BaselineSimulation.Status expected, BaselineSimulation.Status next) {
        if (session.engine.status() != expected) {
            throw new ApiException(409, "INVALID_SIMULATION_STATE", "Command requires simulation status " + expected);
        }
        session.engine.status(next);
    }

}
