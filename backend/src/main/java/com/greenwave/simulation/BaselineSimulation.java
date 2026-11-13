package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.RoadRoute;
import com.greenwave.network.RoadRules;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class BaselineSimulation {
    static final double STEP = 0.1;
    static final double MAX_SPEED = SimulationSettings.DEFAULT.maxSpeed();
    static final double GREEN_BOOST_MPS = 20 / 3.6;
    private static final double EPSILON = 1e-7;

    enum Algorithm { ASTAR, GREENWAVE }
    enum Status { READY, RUNNING, PAUSED, COMPLETED, TIMEOUT, FAILED }
    record StopLine(String id, double distance, List<Double> position, SignalProgram.Type type, int offset) {}
    record Vehicle(List<Double> position, double speedMps) {}
    record Signal(String id, List<Double> position, SignalProgram.Phase phase,
                  Double remainingPhaseSeconds, SignalProgram.Type type) {}
    record Metrics(double distanceM, double elapsedSeconds, Double travelTimeSeconds,
                   int stopCount, double stoppedTimeSeconds, double speedVariationMps, Double fuelLiters, Double accelerationFuelLiters) {}
    enum QueueMotion { MOVING, ACCELERATING, BRAKING, STOPPED }
    enum JunctionType { SIGNAL, JUNCTION }
    record Upcoming(JunctionType type, String nextStreet, double distanceM, Double estimatedSeconds,
                    SignalProgram.Phase phase, Double remainingPhaseSeconds) {}
    record Navigation(String currentStreet, Double roadSpeedLimitMps, Upcoming upcoming) {}
    record GreenApproach(double targetSpeedMps, double estimatedCrossingSeconds, double greenRemainingSeconds) {}
    private record GreenPlan(String signalId, int boostThrough, int greenEnd, double crossingTick, double target) {}
    record RouteOption(String id, boolean selected, List<List<Double>> geometry, double remainingSeconds,
                       double signalWaitSeconds, double congestionDelaySeconds, Double scoreGainSeconds, Double requiredGainSeconds) {}
    enum NoticeReason { SELECTED, COMMITTED, ESTIMATE_CHANGED, CANDIDATE_REMOVED }
    record RouteNotice(String id, double gainSeconds, NoticeReason reason, double atSeconds) {}
    private record TimedNotice(RouteNotice notice, int expiresAt) {}
    record Snapshot(String id, Status status, double simulationTimeSeconds, double playbackRate,
                    List<List<Double>> routeGeometry, List<Double> routeCumulativeMeters, Vehicle vehicle, double effectiveMaxSpeedMps,
                    List<Signal> signals, Metrics metrics, String failure, Navigation navigation, Algorithm algorithm, GreenApproach greenApproach, boolean coasting,
                    Double fuelFlowLitersPerHour, Integer routeCandidates, List<RouteOption> routeOptions, List<RouteNotice> routeNotices, QueueMotion queueMotion) {
        Snapshot {
            routeNotices = routeNotices == null ? List.of() : List.copyOf(routeNotices);
            routeOptions = routeOptions == null ? List.of() : List.copyOf(routeOptions);
        }
    }
    private record YellowDecision(int phaseStart, boolean mayEnter) {}

    private final List<Edge> initialEdges;
    private final SimulationSignals.Scenario scenario;
    private final long seed;
    private final Algorithm algorithm;
    private final GreenWavePlanner planner;
    private final SimulationSettings settings;
    private final SimulationTraffic traffic;
    private final RoadRules rules;
    private final Map<String, YellowDecision> decisions = new HashMap<>();
    private SimulationSignals signals;
    private List<Edge> edges;
    private List<RouteOption> routeOptions = List.of();
    private Map<String, List<Edge>> optionPaths = Map.of();
    private final List<TimedNotice> routeNotices = new ArrayList<>();
    private List<RoadRules.Context> routeContexts;
    private List<StopLine> stopLines;
    private RoadRoute route;
    private double[] edgeStarts;
    private List<Double> routeCumulativeMeters;
    private List<Approach> approaches;
    private List<Maneuver> maneuvers;
    private String coastSignal;
    private boolean coasting;
    private double fuelFlowLitersPerHour;
    private GreenPlan greenPlan;
    private int recoveryThrough;
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
    private double fuelLiters;
    private double accelerationFuelLiters;
    private QueueMotion queueMotion;
    private String failure;

    BaselineSimulation(List<Edge> edges, List<StopLine> stopLines, long seed) {
        this(edges, stopLines, seed, Set.of());
    }

    BaselineSimulation(List<Edge> edges, List<StopLine> stopLines, long seed, Set<String> junctions) {
        this(edges, seed, SimulationSignals.Scenario.fixture(junctions, stopLines), Algorithm.ASTAR, null);
    }

    BaselineSimulation(List<Edge> edges, long seed, SimulationSignals.Scenario scenario, Algorithm algorithm, Network network) {
        this(edges, seed, scenario, algorithm, network, SimulationSettings.DEFAULT);
    }

    BaselineSimulation(List<Edge> edges, long seed, SimulationSignals.Scenario scenario, Algorithm algorithm,
                       Network network, SimulationSettings settings) {
        this(edges, seed, scenario, algorithm, network, settings, SimulationTraffic.EMPTY);
    }

    BaselineSimulation(List<Edge> edges, long seed, SimulationSignals.Scenario scenario, Algorithm algorithm,
                       Network network, SimulationSettings settings, SimulationTraffic traffic) {
        initialEdges = List.copyOf(edges);
        this.traffic = traffic;
        rules = network == null ? RoadRules.EMPTY : network.rules();
        this.settings = settings;
        this.seed = seed;
        this.scenario = scenario;
        this.algorithm = algorithm;
        planner = algorithm == Algorithm.GREENWAVE ? new GreenWavePlanner(network, edges.getLast().toNodeId(), settings) : null;
        reset();
    }

    private void setRoute(List<Edge> routeEdges) {
        edges = List.copyOf(routeEdges);
        var contexts = new ArrayList<RoadRules.Context>();
        var context = RoadRules.START;
        contexts.add(context);
        for (var edge : edges) {
            context = rules.enter(context, edge);
            if (context == null) throw new IllegalArgumentException("Route contains a forbidden maneuver.");
            contexts.add(context);
        }
        routeContexts = List.copyOf(contexts);
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
        coastSignal = null;
        coasting = false;
        fuelFlowLitersPerHour = 0;
        queueMotion = null;
        routeOptions = List.of();
        optionPaths = Map.of();
        routeNotices.clear();
        if (planner != null) planner.resetCandidates();
        greenPlan = null;
        recoveryThrough = -1;
        decisions.clear();
        status = Status.READY;
        tick = edgeIndex = nextStopLine = stopTicks = stopCount = stoppedTicks = 0;
        distance = speed = speedVariation = fuelLiters = accelerationFuelLiters = 0;
        hasMoved = false;
        failure = null;
        setRoute(initialEdges);
    }

    Status status() { return status; }
    String failure() { return failure; }
    void status(Status status) { this.status = status; }

    void advance() {
        if (status != Status.RUNNING) return;
        if (planner != null && tick % settings.decisionTicks() == 0) plan();
        double normal = normalCap(edgeIndex);
        boolean activeGreen = hasGreenPlan();
        if (activeGreen) recoveryThrough = greenPlan.boostThrough();
        if (!activeGreen && speed <= normal + EPSILON) recoveryThrough = -1;
        int boostThrough = activeGreen ? greenPlan.boostThrough() : recoveryThrough;
        double target = roadTarget(distance, speed, edgeIndex, boostThrough, nextSignalDistance());
        if (!activeGreen) target = Math.min(target, Math.max(normal, speed - settings.braking() * STEP));
        target = Math.min(target, speedForConstraint(route.lengthM() - distance, 0, speed));
        target = Math.min(target, trafficTarget(edgeIndex, tick, speed));
        boolean yellowCommitment = false;

        double blockedAt = Double.POSITIVE_INFINITY;
        for (int i = nextStopLine; i < stopLines.size(); i++) {
            var line = stopLines.get(i);
            double ahead = line.distance() - distance;
            if (ahead < -EPSILON) continue;
            if (ahead > brakingLookahead(Math.max(settings.maxSpeed(), speed))) break;
            var phase = signals.at(line.id(), tick);
            if (mustStop(i, line, phase, ahead)) {
                target = Math.min(target, speedForConstraint(ahead, 0, speed));
                blockedAt = Math.min(blockedAt, line.distance());
            } else if (phase.phase() == SignalProgram.Phase.YELLOW) {
                yellowCommitment = true;
            }
        }
        double drag = GasolineFuelModel.coastingDeceleration(speed);
        double coastSpeed = speed - drag * STEP;
        coasting = !activeGreen && !yellowCommitment && drag <= settings.braking()
                && target >= coastSpeed && shouldCoast(normal, target, drag);
        if (coasting) target = coastSpeed;

        double nextSpeed = Math.min(speed + settings.acceleration() * STEP, target);
        if (nextSpeed + EPSILON < Math.max(0, speed - settings.braking() * STEP)) {
            fail("Insufficient braking distance");
            return;
        }
        double movement = (speed + nextSpeed) * STEP / 2;
        if (distance + movement > Math.min(blockedAt, route.lengthM()) + EPSILON) {
            fail("Movement would cross a closed stop line or destination");
            return;
        }
        double nextDistance = Math.min(distance + movement, Math.min(blockedAt, route.lengthM()));
        var stepFuel = GasolineFuelModel.step(speed, nextSpeed, nextDistance - distance, STEP);
        fuelLiters += stepFuel.liters();
        accelerationFuelLiters += stepFuel.accelerationCorrectionLiters();
        fuelFlowLitersPerHour = stepFuel.liters() * 3600 / STEP;
        distance = nextDistance;
        while (nextStopLine < stopLines.size()) {
            double line = stopLines.get(nextStopLine).distance();
            if (line > distance || line >= blockedAt) break;
            nextStopLine++;
        }
        while (edgeIndex + 1 < edges.size() && distance >= edgeStarts[edgeIndex + 1]) edgeIndex++;
        speedVariation += Math.abs(nextSpeed - speed);
        queueMotion = !traffic.hasQueue(edges.get(edgeIndex)) ? null
                : nextSpeed <= 0.1 ? QueueMotion.STOPPED
                : nextSpeed > speed + EPSILON ? QueueMotion.ACCELERATING
                : nextSpeed < speed - EPSILON ? QueueMotion.BRAKING : QueueMotion.MOVING;
        speed = nextSpeed;
        tick++;
        if (route.lengthM() - distance <= EPSILON && speed <= EPSILON) {
            distance = route.lengthM();
            status = Status.COMPLETED;
            coasting = false;
            fuelFlowLitersPerHour = 0;
            return;
        }
        if (speed > 0.1) {
            hasMoved = true;
            stopTicks = 0;
        } else if (hasMoved) {
            stoppedTicks++;
            if (++stopTicks == 10) stopCount++;
        }
        if (tick >= settings.maxTicks()) status = Status.TIMEOUT;
    }

    private double trafficTarget(int edge, int atTick, double velocity) {
        return Math.max(traffic.targetSpeed(edges.get(edge), atTick), velocity - settings.braking() * STEP);
    }

    private double normalCap(int edge) {
        return Math.min(Math.min(settings.maxSpeed(), edges.get(edge).speedLimitMps()), traffic.limit(edges.get(edge)));
    }

    private double nextSignalDistance() {
        return nextStopLine < stopLines.size() ? stopLines.get(nextStopLine).distance() : Double.POSITIVE_INFINITY;
    }

    private double cap(int edge, int boostThrough) {
        return Math.min(traffic.limit(edges.get(edge)), normalCap(edge) + (edge <= boostThrough ? GREEN_BOOST_MPS : 0));
    }

    private double roadTarget(double position, double velocity, int currentEdge, int boostThrough, double stopLine) {
        double permitted = Math.max(cap(currentEdge, boostThrough), velocity - settings.braking() * STEP);
        double target = permitted;
        double signalAhead = stopLine - position;
        double safeSpeed = Math.sqrt(2 * settings.braking() * 29);
        if (signalAhead >= -EPSILON && signalAhead <= brakingLookahead(Math.max(permitted, velocity))) {
            target = Math.min(target, Math.max(safeSpeed, speedForConstraint(signalAhead, 0, velocity)));
        }
        for (int i = currentEdge + 1; i < edges.size(); i++) {
            double ahead = edgeStarts[i] - position;
            if (ahead > brakingLookahead()) break;
            double nextCap = cap(i, boostThrough);
            double buffer = permitted > nextCap || velocity > nextCap ? approachMax() * STEP : 0;
            double limit = ahead <= buffer ? nextCap : speedForConstraint(ahead - buffer, nextCap, velocity);
            target = Math.min(target, limit);
        }
        for (var maneuver : maneuvers) {
            double ahead = maneuver.distance() - position;
            if (ahead < -ManeuverLimits.ZONE_M) continue;
            if (ahead > brakingLookahead() + ManeuverLimits.ZONE_M + approachMax() * STEP) break;
            double brakingDistance = ahead - ManeuverLimits.ZONE_M - approachMax() * STEP;
            double limit = brakingDistance > 0
                    ? speedForConstraint(brakingDistance, maneuver.speed(), velocity) : maneuver.speed();
            target = Math.min(target, limit);
        }
        return target;
    }

    private boolean shouldCoast(double normal, double physicalTarget, double drag) {
        if (coastSignal == null || nextStopLine >= stopLines.size() || !coastSignal.equals(stopLines.get(nextStopLine).id())
                || speed <= GasolineFuelModel.CUTOFF_MPS + 0.15) return false;
        if (coasting) return true;
        if (speed + 0.02 < Math.min(normal, physicalTarget)) return false;
        double coastDistance = (speed * speed - GasolineFuelModel.CUTOFF_MPS * GasolineFuelModel.CUTOFF_MPS) / (2 * drag);
        return nextSignalDistance() - distance <= coastDistance + brakingLookahead(speed);
    }

    private void plan() {
        var edge = edges.get(edgeIndex);
        boolean atOrigin = tick == 0;
        double remaining = atOrigin ? 0 : edgeStarts[edgeIndex + 1] - distance;
        String node = atOrigin ? edge.fromNodeId() : edge.toNodeId();
        var observed = planner.observe(node, remaining, this::signalObservation, traffic, atOrigin ? null : edge);
        boolean recovering = edgeIndex <= recoveryThrough && speed > normalCap(edgeIndex) + EPSILON;
        if (!hasYellowCommitment() && !hasGreenPlan() && !recovering) replanRoute(atOrigin, observed);
        planApproach(observed.signals());
    }

    private void replanRoute(boolean atOrigin, GreenWavePlanner.Observations observed) {
        int prefixSize = atOrigin ? 0 : edgeIndex + 1;
        double lockDistance = brakingLookahead() + ManeuverLimits.ZONE_M + approachMax() * STEP;
        while (!atOrigin && prefixSize < edges.size() && edgeStarts[prefixSize] - distance <= lockDistance) {
            prefixSize++;
        }
        if (prefixSize >= edges.size()) {
            updateRouteOptions(List.of(), Map.of(), prefixSize, null, observed);
            planner.resetCandidates();
            return;
        }
        var visited = new HashSet<String>();
        var estimate = new GreenWavePlanner.Estimate(0, 0, 0, 0);
        for (int i = 0; i < prefixSize; i++) {
            var edge = edges.get(i);
            visited.add(edge.fromNodeId());
            if (i < edgeIndex) continue;
            double meters = edgeStarts[i + 1] - Math.max(distance, edgeStarts[i]);
            estimate = planner.traverse(estimate, i == edgeIndex ? null : edges.get(i - 1), edge, meters, true, observed);
        }
        String anchor = edges.get(prefixSize).fromNodeId();
        var incumbent = edges.subList(prefixSize, edges.size());
        var context = routeContexts.get(prefixSize);
        var suffix = planner.chooseSuffix(anchor, estimate, incumbent, visited, observed, context);
        var currentPosition = vehiclePosition();
        var prefix = edges.subList(0, prefixSize);
        var newPaths = new HashMap<String, List<Edge>>();
        var newOptions = new ArrayList<RouteOption>();
        var selectedEstimate = planner.evaluated().getFirst().estimate();
        for (var candidate : planner.evaluated()) {
            var path = new ArrayList<>(prefix);
            path.addAll(candidate.edges());
            String edgeIds = path.stream().map(Edge::id).collect(Collectors.joining("\n"));
            String id = UUID.nameUUIDFromBytes(edgeIds.getBytes(StandardCharsets.UTF_8)).toString();
            newPaths.put(id, path);
            var geometry = new ArrayList<List<Double>>();
            geometry.add(currentPosition);
            for (var edge : path.subList(edgeIndex, path.size())) {
                geometry.addAll(edge.geometry().subList(1, edge.geometry().size()));
            }
            var forecast = candidate.estimate();
            boolean selected = candidate.edges().equals(suffix);
            boolean explain = !selected && suffix.equals(incumbent);
            newOptions.add(new RouteOption(id, selected, List.copyOf(geometry), forecast.arrival(), forecast.waiting(),
                    forecast.congestion(), explain ? selectedEstimate.score() - forecast.score() : null,
                    explain ? GreenWavePlanner.requiredGain(forecast, selectedEstimate, estimate, context) : null));
        }
        updateRouteOptions(newOptions, newPaths, prefixSize, estimate, observed);
        if (!suffix.equals(incumbent)) {
            var updated = new ArrayList<>(edges.subList(0, prefixSize));
            updated.addAll(suffix);
            setRoute(updated);
        }
    }

    private void updateRouteOptions(List<RouteOption> next, Map<String, List<Edge>> paths, int prefixSize,
                                    GreenWavePlanner.Estimate initial, GreenWavePlanner.Observations observed) {
        routeNotices.removeIf(item -> item.expiresAt() <= tick || paths.containsKey(item.notice().id()));
        if (!routeOptions.isEmpty()) {
            var prefix = edges.subList(0, prefixSize);
            for (var option : routeOptions.subList(1, routeOptions.size())) {
                double gain = routeOptions.getFirst().remainingSeconds() - option.remainingSeconds();
                if (gain < 0.5) continue;
                NoticeReason reason = removalReason(option.id(), next, paths, prefix, initial, observed);
                if (reason != null) {
                    routeNotices.add(new TimedNotice(new RouteNotice(option.id(), gain, reason, tick * STEP), tick + 100));
                }
            }
        }
        routeOptions = List.copyOf(next);
        optionPaths = paths;
    }

    private NoticeReason removalReason(String id, List<RouteOption> next, Map<String, List<Edge>> paths,
                                       List<Edge> prefix, GreenWavePlanner.Estimate initial,
                                       GreenWavePlanner.Observations observed) {
        if (paths.containsKey(id)) {
            return next.getFirst().id().equals(id) ? NoticeReason.SELECTED : null;
        }
        var old = optionPaths.get(id);
        int size = prefix.size();
        if (old.size() < size || !old.subList(0, size).equals(prefix)) return NoticeReason.COMMITTED;
        if (initial != null && !next.isEmpty()) {
            var context = routeContexts.get(size);
            for (var edge : old.subList(size, old.size())) {
                context = rules.enter(context, edge);
                if (context == null) return NoticeReason.CANDIDATE_REMOVED;
            }
            var forecast = planner.estimate(old.subList(size, old.size()), initial, observed, routeContexts.get(size).previous());
            if (forecast.arrival() >= next.getFirst().remainingSeconds()) return NoticeReason.ESTIMATE_CHANGED;
        }
        return NoticeReason.CANDIDATE_REMOVED;
    }

    private void planApproach(Map<String, GreenWavePlanner.Observation> observed) {
        greenPlan = null;
        coastSignal = null;
        if (nextStopLine >= stopLines.size()) return;
        var line = stopLines.get(nextStopLine);
        var visible = observed.get(line.id());
        if (visible == null || line.distance() - distance > settings.observationRange()) return;
        double downstream = nextStopLine + 1 < stopLines.size()
                ? stopLines.get(nextStopLine + 1).distance() : route.lengthM();
        double crossing = forecastCrossing(line.distance(), 450, downstream, -1);
        if (line.type() == SignalProgram.Type.FIXED && visible.phase() == SignalProgram.Phase.GREEN
                && visible.remainingTicks() != null && visible.remainingTicks() > 1) {
            int horizon = visible.remainingTicks() - 1;
            if (crossing <= horizon) return;
            int through = boostThrough(line.distance());
            double boosted = forecastCrossing(line.distance(), horizon, downstream, through);
            if (Double.isFinite(boosted)) {
                greenPlan = new GreenPlan(line.id(), through, tick + visible.remainingTicks(), tick + boosted,
                        cap(edgeIndex, through));
                return;
            }
        }
        if ((Double.isFinite(crossing) && visible.waitSeconds(crossing * STEP) > 0)
                || (coasting && visible.phase() != SignalProgram.Phase.GREEN)) {
            coastSignal = line.id();
        }
    }

    private int boostThrough(double stopAt) {
        int through = edgeIndex;
        while (through + 1 < edges.size() && edgeStarts[through + 1] < stopAt - EPSILON) {
            var from = edges.get(through);
            var to = edges.get(through + 1);
            if (!from.name().equals(to.name()) || Math.abs(from.speedLimitMps() - to.speedLimitMps()) > 0.01
                    || Double.isFinite(ManeuverLimits.speedMps(from, to, false))) break;
            through++;
        }
        return through;
    }

    private GreenWavePlanner.Observation signalObservation(String node) {
        var definition = scenario.signals().get(node);
        if (definition == null) return null;
        var state = signals.at(node, tick);
        Integer cycle = definition.type() == SignalProgram.Type.FIXED ? Math.floorMod(tick + definition.offset(), SignalProgram.FIXED_CYCLE_TICKS) : null;
        return new GreenWavePlanner.Observation(cycle, state.phase(), state.remainingTicks());
    }

    private double forecastCrossing(double stopAt, int horizon, double downstreamStop, int boostThrough) {
        double position = distance;
        double velocity = speed;
        int currentEdge = edgeIndex;
        for (int elapsed = 1; elapsed <= horizon; elapsed++) {
            int permittedThrough = boostThrough >= 0 ? boostThrough
                    : velocity > normalCap(currentEdge) + EPSILON ? recoveryThrough : -1;
            double target = roadTarget(position, velocity, currentEdge, permittedThrough, nextSignalDistance());
            if (boostThrough < 0) {
                target = Math.min(target, Math.max(normalCap(currentEdge), velocity - settings.braking() * STEP));
            }
            target = Math.min(target, speedForConstraint(downstreamStop - position, 0, velocity));
            target = Math.min(target, trafficTarget(currentEdge, tick + elapsed - 1, velocity));
            double next = Math.min(velocity + settings.acceleration() * STEP, target);
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
                    forecastCrossing(line.distance(), phase.remainingTicks(), downstream, -1));
            decision = new YellowDecision(phase.phaseStart(), canCross);
            decisions.put(line.id(), decision);
        }
        return !decision.mayEnter();
    }

    static boolean yellowPermits(double distance, double speed) {
        return distance <= 30 && speed > 0.1;
    }

    private double brakingLookahead() {
        return brakingLookahead(approachMax());
    }

    private double approachMax() {
        return settings.maxSpeed() + (planner == null ? 0 : GREEN_BOOST_MPS);
    }

    private double brakingLookahead(double maxSpeed) {
        double discreteMargin = settings.braking() * STEP * STEP / 8 + EPSILON;
        return maxSpeed * maxSpeed / (2 * settings.braking()) + maxSpeed * STEP + discreteMargin;
    }

    private double speedForConstraint(double ahead, double terminalSpeed, double speed) {
        double half = settings.braking() * STEP / 2;
        double radicand = half * half + terminalSpeed * terminalSpeed
                + 2 * settings.braking() * Math.max(0, ahead) - settings.braking() * STEP * speed;
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
        double residual = nextSpeed % (settings.braking() * STEP);
        double remaining = nextSpeed * nextSpeed / (2 * settings.braking())
                + residual * (STEP - residual / settings.braking()) / 2;
        return (speed + nextSpeed) * STEP / 2 + remaining;
    }

    private void fail(String reason) {
        status = Status.FAILED;
        failure = reason;
    }

    Snapshot snapshot(String id, double rate) {
        return snapshot(id, rate, tick);
    }

    Snapshot snapshot(String id, double rate, int signalTick) {
        double time = tick * STEP;
        var signals = new ArrayList<Signal>();
        for (int i = 0; i < stopLines.size(); i++) {
            var line = stopLines.get(i);
            var state = this.signals.at(line.id(), signalTick);
            Double remaining = state.remainingTicks() == null ? null : state.remainingTicks() * STEP;
            signals.add(new Signal(line.id(), line.position(), state.phase(), remaining, line.type()));
        }
        return new Snapshot(id, status, time, rate, route.geometry(), routeCumulativeMeters, new Vehicle(vehiclePosition(), speed), settings.maxSpeed(),
                List.copyOf(signals), new Metrics(distance, time, status == Status.COMPLETED ? time : null,
                stopCount, stoppedTicks * STEP, speedVariation, fuelLiters, accelerationFuelLiters), failure, navigation(signals), algorithm, greenApproach(), status == Status.RUNNING && coasting,
                status == Status.RUNNING ? fuelFlowLitersPerHour : 0, planner == null ? null : planner.candidatesEvaluated(), routeOptions,
                routeNotices.stream().filter(item -> item.expiresAt() > tick).map(TimedNotice::notice).toList(),
                status == Status.RUNNING || status == Status.PAUSED ? queueMotion : null);
    }

    private List<Double> vehiclePosition() {
        var edge = edges.get(edgeIndex);
        double fraction = Math.max(0, Math.min(1, (distance - edgeStarts[edgeIndex]) / edge.lengthM()));
        var a = edge.geometry().getFirst();
        var b = edge.geometry().getLast();
        return List.of(a.get(0) + (b.get(0) - a.get(0)) * fraction,
                a.get(1) + (b.get(1) - a.get(1)) * fraction);
    }

    private boolean hasGreenPlan() {
        return greenPlan != null && (status == Status.RUNNING || status == Status.PAUSED)
                && edgeIndex <= greenPlan.boostThrough() && tick < greenPlan.greenEnd() && nextStopLine < stopLines.size()
                && greenPlan.signalId().equals(stopLines.get(nextStopLine).id());
    }

    private GreenApproach greenApproach() {
        if (!hasGreenPlan()) return null;
        return new GreenApproach(greenPlan.target(), Math.max(0, greenPlan.crossingTick() - tick) * STEP,
                (greenPlan.greenEnd() - tick) * STEP);
    }

    private Navigation navigation(List<Signal> signals) {
        var currentRoad = edges.get(edgeIndex);
        for (var approach : approaches) {
            if (approach.signalIndex() >= 0 ? approach.signalIndex() < nextStopLine : approach.edgeIndex() < edgeIndex) {
                continue;
            }
            double ahead = Math.max(0, edgeStarts[approach.edgeIndex() + 1] - distance);
            var signal = approach.signalIndex() < 0 ? null : signals.get(approach.signalIndex());
            return new Navigation(currentRoad.name(), currentRoad.speedLimitMps(), new Upcoming(signal == null ? JunctionType.JUNCTION : JunctionType.SIGNAL,
                    edges.get(approach.edgeIndex() + 1).name(), ahead, speed > 0.1 ? ahead / speed : null,
                    signal == null ? null : signal.phase(), signal == null ? null : signal.remainingPhaseSeconds()));
        }
        return new Navigation(currentRoad.name(), currentRoad.speedLimitMps(), null);
    }

}
