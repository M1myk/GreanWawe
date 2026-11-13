package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.trip.api.dto.TripConfigRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.greenwave.simulation.ComparisonSnapshot.Status;

final class ComparisonSession {
    static final String ENGINE_VERSION = "0.7.10";
    record Definition(TripConfigRequest config, Network network, String scenarioVersion,
                      SimulationSettings settings, SimulationSignals.Scenario signals, List<Edge> edges, SimulationTraffic traffic) {}
    record Applied(ComparisonController.Command command, ComparisonSnapshot snapshot) {}

    final String id;
    final Definition definition;
    final String createdAt;
    final Map<String, Applied> commands = new LinkedHashMap<>();
    private final BaselineSimulation baseline;
    private final BaselineSimulation greenWave;
    long revision;
    long commandRevision;
    long lastAccess = System.nanoTime();
    double rate = 1;
    private double pendingTicks;
    private int tick;
    private Status status = Status.READY;
    private ComparisonSnapshot.Failure failure;
    private String finishedAt;
    private boolean estimatedAtGreenWaveArrival;
    private boolean requestedExample;
    SavedTimeExamples.Draft exampleDraft;
    SavedTimeExamples.Selection savedTimeExample;

    ComparisonSession(String id, Definition definition, String createdAt) {
        this.id = id;
        this.definition = definition;
        this.createdAt = createdAt;
        baseline = vehicle(BaselineSimulation.Algorithm.ASTAR);
        greenWave = vehicle(BaselineSimulation.Algorithm.GREENWAVE);
    }

    private BaselineSimulation vehicle(BaselineSimulation.Algorithm algorithm) {
        return new BaselineSimulation(definition.edges(), definition.config().randomSeed(), definition.signals(),
                algorithm, definition.network(), definition.settings(), definition.traffic());
    }

    Status status() { return status; }

    void transition(Status next, BaselineSimulation.Status fromVehicle, BaselineSimulation.Status toVehicle) {
        status = next;
        if (baseline.status() == fromVehicle) baseline.status(toVehicle);
        if (greenWave.status() == fromVehicle) greenWave.status(toVehicle);
    }

    void pulse() {
        if (status != Status.RUNNING) return;
        pendingTicks += rate;
        while (pendingTicks >= 1 && status == Status.RUNNING) {
            pendingTicks--;
            baseline.advance();
            greenWave.advance();
            tick++;
            revision++;
            if (baseline.status() == BaselineSimulation.Status.FAILED || greenWave.status() == BaselineSimulation.Status.FAILED) {
                String reason = baseline.failure() == null ? greenWave.failure() : baseline.failure();
                fail("ENGINE_FAILURE", reason);
            } else if (finished(baseline) && finished(greenWave)) {
                status = Status.COMPLETED;
                finishedAt = Instant.now().toString();
            }
        }
    }

    int takeExampleEstimate() {
        if (requestedExample || greenWave.status() != BaselineSimulation.Status.COMPLETED
                || baseline.status() != BaselineSimulation.Status.RUNNING) return 0;

        double elapsedLead = (tick - greenWave.elapsedTicks()) * BaselineSimulation.STEP;
        if (estimatedAtGreenWaveArrival && elapsedLead < SavedTimeExamples.MINIMUM_SECONDS) return 0;
        estimatedAtGreenWaveArrival = true;
        int estimate = SavedTimeExamples.wholeSeconds(elapsedLead + baseline.estimatedRemainingSeconds());
        requestedExample = estimate >= SavedTimeExamples.MINIMUM_SECONDS;
        return requestedExample ? estimate : 0;
    }

    void fail(String code, String reason) {
        failure = new ComparisonSnapshot.Failure(code, reason);
        status = Status.FAILED;
        finishedAt = Instant.now().toString();
        revision++;
        if (!finished(baseline)) baseline.status(BaselineSimulation.Status.FAILED);
        if (!finished(greenWave)) greenWave.status(BaselineSimulation.Status.FAILED);
    }

    private static boolean finished(BaselineSimulation engine) {
        return engine.status() == BaselineSimulation.Status.COMPLETED || engine.status() == BaselineSimulation.Status.TIMEOUT;
    }

    ComparisonSnapshot snapshot() {
        var astar = baseline.snapshot(id + ":astar", rate, tick);
        var green = greenWave.snapshot(id + ":greenwave", rate, tick);
        Double astarTime = astar.metrics().travelTimeSeconds();
        Double greenTime = green.metrics().travelTimeSeconds();
        Double saving = status == Status.COMPLETED && astarTime != null && greenTime != null ? astarTime - greenTime : null;
        Double percent = saving != null && astarTime > 0 ? saving * 100 / astarTime : null;
        return new ComparisonSnapshot(id, revision, status, tick * BaselineSimulation.STEP, rate,
                definition.config(), definition.network().version(), definition.scenarioVersion(), ENGINE_VERSION,
                createdAt, finishedAt, failure, List.of(astar, green), saving, percent,
                savedTimeExample == null ? null : savedTimeExample.text(),
                savedTimeExample == null ? null : savedTimeExample.source());
    }
}
