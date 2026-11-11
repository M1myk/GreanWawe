package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.RoadRoute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BaselineSimulation {
    static final double STEP = 0.1;
    static final double ACCELERATION = 2;
    static final double BRAKING = 3;
    static final double MAX_SPEED = Math.min(13.89, Math.sqrt(2 * BRAKING * 29));
    static final int MAX_TICKS = 18_000;
    private static final double EPSILON = 1e-7;

    enum Algorithm { ASTAR, GREENWAVE }
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
                    List<Signal> signals, Metrics metrics, String failure, Navigation navigation, Algorithm algorithm) {}
    private record YellowDecision(int phaseStart, boolean mayEnter) {}

    private final List<Edge> initialEdges;
    private final SimulationSignals.Scenario scenario;
    private final long seed;
    private final Algorithm algorithm;
    private final GreenWavePlanner planner;
    private final Map<String, YellowDecision> decisions = new HashMap<>();
    private SimulationSignals signals;
    private List<Edge> edges;
    private List<StopLine> stopLines;
    private RoadRoute route;
    private double[] edgeStarts;
    private List<Double> routeCumulativeMeters;
    private List<Approach> approaches;
    private List<Maneuver> maneuvers;
    private double advisorySpeed;
    private record Maneuver(double distance, double speed) {}
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

    BaselineSimulation(List<Edge> edges, List<StopLine> stopLines, long seed) {
        this(edges, stopLines, seed, Set.of());
    }

    BaselineSimulation(List<Edge> edges, List<StopLine> stopLines, long seed, Set<String> junctions) {
        this(edges, seed, SimulationSignals.Scenario.fixture(junctions, stopLines), Algorithm.ASTAR, null);
    }

    BaselineSimulation(List<Edge> edges, long seed, SimulationSignals.Scenario scenario, Algorithm algorithm, Network network) {
        initialEdges = List.copyOf(edges);
        this.seed = seed;
        this.scenario = scenario;
        this.algorithm = algorithm;
        planner = algorithm == Algorithm.GREENWAVE ? new GreenWavePlanner(network, edges.getLast().toNodeId()) : null;
        reset();
    }

    private void setRoute(List<Edge> routeEdges) {
        edges = List.copyOf(routeEdges);
        stopLines = signals.routeLines(edges);
        route = RoadRoute.fromEdges(edges);
        edgeStarts = new double[edges.size() + 1];
        for (int i = 0; i < edges.size(); i++) edgeStarts[i + 1] = edgeStarts[i] + edges.get(i).lengthM();
        routeCumulativeMeters = Arrays.stream(edgeStarts).boxed().toList();
        var limits = new ArrayList<Maneuver>();
        var approachList = new ArrayList<Approach>();
        int signalIndex = 0;
        for (int i = 0; i < edges.size() - 1; i++) {
            String node = edges.get(i).toNodeId();
            boolean signal = signalIndex < stopLines.size() && stopLines.get(signalIndex).id().equals(node);
            boolean junction = scenario.junctions().contains(node);
            double limit = ManeuverLimits.speedMps(edges.get(i), edges.get(i + 1), signal || junction);
            if (Double.isFinite(limit)) limits.add(new Maneuver(edgeStarts[i + 1], limit));
            if (signal) {
                approachList.add(new Approach(i, signalIndex++));
            } else if (junction) {
                approachList.add(new Approach(i, -1));
            }
        }
        approaches = List.copyOf(approachList);
        maneuvers = List.copyOf(limits);
    }

    void reset() {
        signals = new SimulationSignals(scenario, seed);
        advisorySpeed = MAX_SPEED;
        decisions.clear();
        status = Status.READY;
        tick = edgeIndex = nextStopLine = stopTicks = stopCount = stoppedTicks = 0;
        distance = speed = speedVariation = 0;
        hasMoved = false;
        failure = null;
        setRoute(initialEdges);
    }

    Status status() { return status; }
    void status(Status status) { this.status = status; }

    void advance() {
        if (status != Status.RUNNING) return;
        if (planner != null && tick % GreenWavePlanner.DECISION_TICKS == 0) plan();
        double target = roadTarget(distance, speed, edgeIndex);
        target = Math.min(target, speedForConstraint(route.lengthM() - distance, 0, speed));
        boolean yellowCommitment = false;

        double blockedAt = Double.POSITIVE_INFINITY;
        for (int i = nextStopLine; i < stopLines.size(); i++) {
            var line = stopLines.get(i);
            double ahead = line.distance() - distance;
            if (ahead < -EPSILON) continue;
            if (ahead > brakingLookahead()) break;
            var phase = signals.at(line.id(), tick);
            if (mustStop(i, line, phase, ahead)) {
                target = Math.min(target, speedForConstraint(ahead, 0, speed));
                blockedAt = Math.min(blockedAt, line.distance());
            } else if (phase.phase() == SignalProgram.Phase.YELLOW) {
                yellowCommitment = true;
            }
        }
        if (!yellowCommitment) target = Math.min(target, Math.max(advisorySpeed, speed - BRAKING * STEP));

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

    private double roadTarget(double position, double velocity, int currentEdge) {
        double target = Math.min(MAX_SPEED, edges.get(currentEdge).speedLimitMps());
        for (int i = currentEdge + 1; i < edges.size(); i++) {
            double ahead = edgeStarts[i] - position;
            if (ahead > brakingLookahead()) break;
            target = Math.min(target, speedForConstraint(ahead, edges.get(i).speedLimitMps(), velocity));
        }
        for (var maneuver : maneuvers) {
            double ahead = maneuver.distance() - position;
            if (ahead < -ManeuverLimits.ZONE_M) continue;
            if (ahead > brakingLookahead() + ManeuverLimits.ZONE_M + MAX_SPEED * STEP) break;
            double brakingDistance = ahead - ManeuverLimits.ZONE_M - MAX_SPEED * STEP;
            double limit = brakingDistance > 0
                    ? speedForConstraint(brakingDistance, maneuver.speed(), velocity) : maneuver.speed();
            target = Math.min(target, limit);
        }
        return target;
    }

    private void plan() {
        var edge = edges.get(edgeIndex);
        boolean atOrigin = tick == 0;
        double remaining = atOrigin ? 0 : edgeStarts[edgeIndex + 1] - distance;
        String node = atOrigin ? edge.fromNodeId() : edge.toNodeId();
        double seconds = remaining / Math.min(MAX_SPEED, edge.speedLimitMps());
        var observed = planner.observe(node, remaining, seconds, this::signalObservation);
        if (atOrigin || (!hasYellowCommitment() && remaining > brakingLookahead() + ManeuverLimits.ZONE_M + MAX_SPEED * STEP)) {
            int prefixSize = atOrigin ? 0 : edgeIndex + 1;
            var visited = new HashSet<String>();
            for (int i = 0; i < prefixSize; i++) visited.add(edges.get(i).fromNodeId());
            var incumbent = edges.subList(prefixSize, edges.size());
            var suffix = planner.chooseSuffix(node, incumbent, visited, observed);
            if (!suffix.equals(incumbent)) {
                var updated = new ArrayList<>(edges.subList(0, prefixSize));
                updated.addAll(suffix);
                // The unchanged prefix preserves the current edge and crossed stop lines.
                setRoute(updated);
                observed = planner.observe(node, remaining, seconds, this::signalObservation);
            }
        }
        advisorySpeed = MAX_SPEED;
        if (nextStopLine >= stopLines.size()) return;
        var line = stopLines.get(nextStopLine);
        var visible = observed.get(line.id());
        if (line.type() != SignalProgram.Type.FIXED || visible == null
                || line.distance() - distance > GreenWavePlanner.RANGE_M) return;
        advisorySpeed = GreenWavePlanner.chooseSpeed(speed, visible.state(),
                candidate -> forecastCrossing(line.distance(), candidate, 1200, route.lengthM()));
    }

    private GreenWavePlanner.Observation signalObservation(String node) {
        var definition = scenario.signals().get(node);
        if (definition == null) return null;
        var state = signals.at(node, tick);
        Integer cycle = definition.type() == SignalProgram.Type.FIXED ? Math.floorMod(tick + definition.offset(), 600) : null;
        return new GreenWavePlanner.Observation(cycle, state.phase(), state.remainingTicks());
    }

    private double forecastCrossing(double stopAt, double candidate, int horizon, double downstreamStop) {
        double position = distance;
        double velocity = speed;
        int currentEdge = edgeIndex;
        for (int elapsed = 1; elapsed <= horizon; elapsed++) {
            double target = roadTarget(position, velocity, currentEdge);
            target = Math.min(target, speedForConstraint(downstreamStop - position, 0, velocity));
            target = Math.min(target, Math.max(candidate, velocity - BRAKING * STEP));
            double next = Math.min(velocity + ACCELERATION * STEP, target);
            position += (velocity + next) * STEP / 2;
            if (position > stopAt + EPSILON) return elapsed;
            velocity = next;
            while (currentEdge + 1 < edges.size() && position >= edgeStarts[currentEdge + 1]) currentEdge++;
        }
        return Double.POSITIVE_INFINITY;
    }

    private boolean hasYellowCommitment() {
        for (int i = nextStopLine; i < stopLines.size(); i++) {
            var line = stopLines.get(i);
            if (line.distance() - distance > 30) break;
            var decision = decisions.get(line.id());
            if (decision != null && decision.mayEnter() && signals.at(line.id(), tick).phase() == SignalProgram.Phase.YELLOW) {
                return true;
            }
        }
        return false;
    }

    private boolean mustStop(int index, StopLine line, SignalProgram.State phase, double ahead) {
        if (phase.phase() == SignalProgram.Phase.GREEN) return false;
        if (phase.phase() == SignalProgram.Phase.RED) return true;
        var decision = decisions.get(line.id());
        if (decision == null || decision.phaseStart() != phase.phaseStart()) {
            double downstream = index + 1 < stopLines.size() ? stopLines.get(index + 1).distance() : route.lengthM();
            boolean canCross = yellowPermits(ahead, speed) && Double.isFinite(
                    forecastCrossing(line.distance(), MAX_SPEED, phase.remainingTicks(), downstream));
            decision = new YellowDecision(phase.phaseStart(), canCross);
            decisions.put(line.id(), decision);
        }
        return !decision.mayEnter();
    }

    static boolean yellowPermits(double distance, double speed) {
        return distance <= 30 && speed > 0.1;
    }

    private double brakingLookahead() {
        return MAX_SPEED * MAX_SPEED / (2 * BRAKING) + MAX_SPEED * STEP;
    }

    private double speedForConstraint(double ahead, double terminalSpeed, double speed) {
        double half = BRAKING * STEP / 2;
        double radicand = half * half + terminalSpeed * terminalSpeed
                + 2 * BRAKING * Math.max(0, ahead) - BRAKING * STEP * speed;
        double upper = Math.max(0, Math.sqrt(Math.max(0, radicand)) - half);
        if (terminalSpeed != 0 || upper == 0 || stopDistanceAfterStep(upper, speed) <= ahead) return upper;
        // The final, partial braking increment still occupies a full simulation tick.
        double lower = 0;
        for (int i = 0; i < 40; i++) {
            double middle = (lower + upper) / 2;
            if (stopDistanceAfterStep(middle, speed) <= ahead) lower = middle;
            else upper = middle;
        }
        return lower;
    }

    private double stopDistanceAfterStep(double nextSpeed, double speed) {
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
            var state = this.signals.at(line.id(), tick);
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
                stopCount, stoppedTicks * STEP, speedVariation), failure, navigation(signals), algorithm);
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
