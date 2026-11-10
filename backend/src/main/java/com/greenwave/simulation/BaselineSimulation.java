package com.greenwave.simulation;

import com.greenwave.network.NetworkController;
import com.greenwave.network.RoadRoute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

final class BaselineSimulation {
    static final double STEP = 0.1;
    static final double ACCELERATION = 2;
    static final double BRAKING = 3;
    static final double MAX_SPEED = Math.min(13.89, Math.sqrt(2 * BRAKING * 29));
    static final int MAX_TICKS = 18_000;
    private static final double EPSILON = 1e-7;

    enum Status { READY, RUNNING, PAUSED, COMPLETED, TIMEOUT, FAILED }
    record StopLine(String id, double distance, List<Double> position, SignalProgram.Type type, int offset) {}
    record Vehicle(List<Double> position, double speedMps) {}
    record Signal(String id, List<Double> position, SignalProgram.Phase phase,
                  Double remainingPhaseSeconds, SignalProgram.Type type) {}
    record Metrics(double distanceM, double elapsedSeconds, Double travelTimeSeconds,
                   int stopCount, double stoppedTimeSeconds, double speedVariationMps) {}
    enum JunctionType { SIGNAL, JUNCTION }
    record Upcoming(JunctionType type, String nextStreet, double distanceM, Double estimatedSeconds,
                    SignalProgram.Phase phase, Double remainingPhaseSeconds) {}
    record Navigation(String currentStreet, Upcoming upcoming) {}
    record Snapshot(String id, Status status, double simulationTimeSeconds, double playbackRate,
                    List<List<Double>> routeGeometry, List<Double> routeCumulativeMeters, Vehicle vehicle, double effectiveMaxSpeedMps,
                    List<Signal> signals, Metrics metrics, String failure, Navigation navigation) {}
    private record YellowDecision(int phaseStart, boolean mayEnter) {}

    private final List<NetworkController.Edge> edges;
    private final List<StopLine> stopLines;
    private final List<SignalProgram> programs = new ArrayList<>();
    private final Map<String, YellowDecision> decisions = new HashMap<>();
    private final RoadRoute route;
    private final double[] edgeStarts;
    private final long seed;
    private final List<Double> routeCumulativeMeters;
    private final List<Approach> approaches;
    private record Approach(int edgeIndex, int signalIndex) {}
    private Status status = Status.READY;
    private int tick;
    private int edgeIndex;
    private int nextStopLine;
    private double distance;
    private double speed;
    private boolean hasMoved;
    private int stopTicks;
    private int stopCount;
    private int stoppedTicks;
    private double speedVariation;
    private String failure;

    BaselineSimulation(List<NetworkController.Edge> edges, List<StopLine> stopLines, long seed) {
        this(edges, stopLines, seed, Set.of());
    }

    BaselineSimulation(List<NetworkController.Edge> edges, List<StopLine> stopLines, long seed, Set<String> junctions) {
        this.edges = List.copyOf(edges);
        this.stopLines = List.copyOf(stopLines);
        this.seed = seed;
        this.route = RoadRoute.fromEdges(edges);
        this.edgeStarts = new double[edges.size() + 1];
        for (int i = 0; i < edges.size(); i++) edgeStarts[i + 1] = edgeStarts[i] + edges.get(i).lengthM();
        routeCumulativeMeters = Arrays.stream(edgeStarts).boxed().toList();
        var approachList = new ArrayList<Approach>();
        int signalIndex = 0;
        for (int i = 0; i < edges.size() - 1; i++) {
            String node = edges.get(i).toNodeId();
            if (signalIndex < stopLines.size() && stopLines.get(signalIndex).id().equals(node)) {
                approachList.add(new Approach(i, signalIndex++));
            } else if (junctions.contains(node)) {
                approachList.add(new Approach(i, -1));
            }
        }
        approaches = List.copyOf(approachList);
        reset();
    }

    void reset() {
        programs.clear();
        for (var line : stopLines) {
            programs.add(new SignalProgram(line.type(), line.offset(), seed ^ line.id().hashCode()));
        }
        decisions.clear();
        status = Status.READY;
        tick = edgeIndex = nextStopLine = stopTicks = stopCount = stoppedTicks = 0;
        distance = speed = speedVariation = 0;
        hasMoved = false;
        failure = null;
    }

    Status status() { return status; }
    void status(Status status) { this.status = status; }

    void advance() {
        if (status != Status.RUNNING) return;
        double target = Math.min(MAX_SPEED, edges.get(edgeIndex).speedLimitMps());
        double remaining = route.lengthM() - distance;
        target = Math.min(target, speedForConstraint(remaining, 0));
        for (int i = edgeIndex + 1; i < edges.size(); i++) {
            double ahead = edgeStarts[i] - distance;
            if (ahead > brakingLookahead()) break;
            target = Math.min(target, speedForConstraint(ahead, edges.get(i).speedLimitMps()));
        }

        double blockedAt = Double.POSITIVE_INFINITY;
        for (int i = nextStopLine; i < stopLines.size(); i++) {
            var line = stopLines.get(i);
            double ahead = line.distance() - distance;
            if (ahead < -EPSILON) continue;
            if (ahead > brakingLookahead()) break;
            var phase = programs.get(i).at(tick);
            if (mustStop(line, phase, ahead)) {
                target = Math.min(target, speedForConstraint(ahead, 0));
                blockedAt = Math.min(blockedAt, line.distance());
            }
        }

        double nextSpeed = Math.min(speed + ACCELERATION * STEP, target);
        if (nextSpeed + EPSILON < Math.max(0, speed - BRAKING * STEP)) {
            fail("Insufficient braking distance");
            return;
        }
        double movement = (speed + nextSpeed) * STEP / 2;
        if (distance + movement > Math.min(blockedAt, route.lengthM()) + EPSILON) {
            fail("Movement would cross a closed stop line or destination");
            return;
        }
        distance = Math.min(distance + movement, Math.min(blockedAt, route.lengthM()));
        while (nextStopLine < stopLines.size()) {
            double line = stopLines.get(nextStopLine).distance();
            if (line > distance || line >= blockedAt) break;
            nextStopLine++;
        }
        while (edgeIndex + 1 < edges.size() && distance >= edgeStarts[edgeIndex + 1]) edgeIndex++;
        speedVariation += Math.abs(nextSpeed - speed);
        speed = nextSpeed;
        tick++;
        for (var program : programs) program.advance(tick);
        if (route.lengthM() - distance <= EPSILON && speed <= EPSILON) {
            distance = route.lengthM();
            status = Status.COMPLETED;
            return;
        }
        if (speed > 0.1) {
            hasMoved = true;
            stopTicks = 0;
        } else if (hasMoved) {
            stoppedTicks++;
            if (++stopTicks == 10) stopCount++;
        }
        if (tick >= MAX_TICKS) status = Status.TIMEOUT;
    }

    private boolean mustStop(StopLine line, SignalProgram.State phase, double ahead) {
        if (phase.phase() == SignalProgram.Phase.GREEN) return false;
        if (phase.phase() == SignalProgram.Phase.RED) return true;
        var decision = decisions.get(line.id());
        if (decision == null || decision.phaseStart() != phase.phaseStart()) {
            decision = new YellowDecision(phase.phaseStart(), yellowPermits(ahead, speed));
        }
        double timeLeft = phase.remainingTicks() * STEP;
        if (decision.mayEnter() && ahead >= speed * timeLeft - EPSILON) {
            decision = new YellowDecision(phase.phaseStart(), false);
        }
        decisions.put(line.id(), decision);
        return !decision.mayEnter();
    }

    static boolean yellowPermits(double distance, double speed) {
        return distance <= 30 && speed > 0.1;
    }

    private double brakingLookahead() {
        return MAX_SPEED * MAX_SPEED / (2 * BRAKING) + MAX_SPEED * STEP;
    }

    private double speedForConstraint(double ahead, double terminalSpeed) {
        double half = BRAKING * STEP / 2;
        double radicand = half * half + terminalSpeed * terminalSpeed
                + 2 * BRAKING * Math.max(0, ahead) - BRAKING * STEP * speed;
        double upper = Math.max(0, Math.sqrt(Math.max(0, radicand)) - half);
        if (terminalSpeed != 0 || upper == 0 || stopDistanceAfterStep(upper) <= ahead) return upper;
        // The final, partial braking increment still occupies a full simulation tick.
        double lower = 0;
        for (int i = 0; i < 40; i++) {
            double middle = (lower + upper) / 2;
            if (stopDistanceAfterStep(middle) <= ahead) lower = middle;
            else upper = middle;
        }
        return lower;
    }

    private double stopDistanceAfterStep(double nextSpeed) {
        double residual = nextSpeed % (BRAKING * STEP);
        double remaining = nextSpeed * nextSpeed / (2 * BRAKING)
                + residual * (STEP - residual / BRAKING) / 2;
        return (speed + nextSpeed) * STEP / 2 + remaining;
    }

    private void fail(String reason) {
        status = Status.FAILED;
        failure = reason;
    }

    Snapshot snapshot(String id, double rate) {
        double time = tick * STEP;
        var signals = new ArrayList<Signal>();
        for (int i = 0; i < stopLines.size(); i++) {
            var line = stopLines.get(i);
            var state = programs.get(i).at(tick);
            Double remaining = state.remainingTicks() == null ? null : state.remainingTicks() * STEP;
            signals.add(new Signal(line.id(), line.position(), state.phase(), remaining, line.type()));
        }
        var edge = edges.get(edgeIndex);
        double fraction = Math.max(0, Math.min(1, (distance - edgeStarts[edgeIndex]) / edge.lengthM()));
        var a = edge.geometry().getFirst();
        var b = edge.geometry().getLast();
        var position = List.of(a.get(0) + (b.get(0) - a.get(0)) * fraction,
                a.get(1) + (b.get(1) - a.get(1)) * fraction);
        return new Snapshot(id, status, time, rate, route.geometry(), routeCumulativeMeters, new Vehicle(position, speed), MAX_SPEED,
                List.copyOf(signals), new Metrics(distance, time, status == Status.COMPLETED ? time : null,
                stopCount, stoppedTicks * STEP, speedVariation), failure, navigation(signals));
    }

    private Navigation navigation(List<Signal> signals) {
        String street = edges.get(edgeIndex).name();
        for (var approach : approaches) {
            if (approach.signalIndex() >= 0 ? approach.signalIndex() < nextStopLine : approach.edgeIndex() < edgeIndex) {
                continue;
            }
            double ahead = Math.max(0, edgeStarts[approach.edgeIndex() + 1] - distance);
            var signal = approach.signalIndex() < 0 ? null : signals.get(approach.signalIndex());
            return new Navigation(street, new Upcoming(signal == null ? JunctionType.JUNCTION : JunctionType.SIGNAL,
                    edges.get(approach.edgeIndex() + 1).name(), ahead, speed > 0.1 ? ahead / speed : null,
                    signal == null ? null : signal.phase(), signal == null ? null : signal.remainingPhaseSeconds()));
        }
        return new Navigation(street, null);
    }

}
