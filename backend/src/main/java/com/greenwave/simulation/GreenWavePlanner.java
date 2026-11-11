package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.RoadRoute;
import com.greenwave.network.RoadRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class GreenWavePlanner {
    static final int ROUTE_LIMIT = 10;
    private static final int SPUR_SEARCH_LIMIT = 24;
    private static final int SPUR_VISIT_LIMIT = 1000;
    private static final double SPUR_EXTRA_SECONDS = 65;
    private static final double QUEUE_RISK_WEIGHT = 0.5;
    private static final double EPSILON_SECONDS = 1e-7;

    record Observation(Integer fixedCycleTick, SignalProgram.Phase phase, Integer remainingTicks) {
        double waitSeconds(double arrivalSeconds) {
            if (fixedCycleTick != null) {
                double cycle = (fixedCycleTick + arrivalSeconds / BaselineSimulation.STEP) % 600;
                return cycle < 300 ? 0 : (600 - cycle) * BaselineSimulation.STEP;
            }
            if (phase == SignalProgram.Phase.GREEN || remainingTicks == null) return 0;
            int closedTicks = remainingTicks + (phase == SignalProgram.Phase.YELLOW ? 100 : 0);
            return Math.max(0, closedTicks * BaselineSimulation.STEP - arrivalSeconds);
        }
    }

    record Observations(Map<String, Observation> signals, Map<String, SimulationTraffic.SpeedObservation> speeds) {
        double travelSeconds(Edge edge, double meters, double vehicleMax) {
            double normalCap = Math.min(vehicleMax, edge.speedLimitMps());
            var traffic = speeds.get(edge.id());
            return meters / (traffic == null ? normalCap : traffic.expectedSpeed(normalCap));
        }

        double congestionDelay(Edge edge, double meters, double vehicleMax) {
            return travelSeconds(edge, meters, vehicleMax)
                    - meters / Math.min(vehicleMax, edge.speedLimitMps());
        }
    }
    private static final Observations NO_OBSERVATIONS = new Observations(Map.of(), Map.of());

    private record Reach(String node, double meters) {}
    private record FreeFlow(String node, double seconds) {}
    record Estimate(double arrival, double waiting, double congestion, int maneuvers) {
        double score() { return arrival + QUEUE_RISK_WEIGHT * congestion; }
    }
    record Candidate(List<Edge> edges, Estimate estimate) {}
    private record SearchKey(String incoming, List<RoadRules.Progress> restrictions, Set<String> roundabouts) {}
    private record Visit(String node, Estimate estimate, double lowerBound, Visit previous, Edge edge, RoadRules.Context context) {}

    private final SimulationSettings settings;
    private final String destination;
    private final RoadRules rules;
    private final Map<String, List<Edge>> outgoing;
    private final Map<String, Double> freeFlowToDestination;
    private String cachedStart;
    private RoadRules.Context cachedContext;
    private Set<String> cachedVisited = Set.of();
    private List<List<Edge>> cachedAlternatives = List.of();
    private List<Candidate> evaluated = List.of();

    GreenWavePlanner(Network network, String destination, SimulationSettings settings) {
        this.settings = settings;
        rules = network.rules();
        this.destination = destination;
        outgoing = network.edges().stream().collect(Collectors.groupingBy(Edge::fromNodeId));
        freeFlowToDestination = computeFreeFlowBounds(network.edges());
    }

    Map<String, Observation> observe(String node, double meters, Function<String, Observation> readVisibleSignal) {
        return observe(node, meters, readVisibleSignal, SimulationTraffic.EMPTY, null).signals();
    }

    Observations observe(String node, double meters, Function<String, Observation> readVisibleSignal,
                         SimulationTraffic traffic, Edge activeEdge) {
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Reach::meters).thenComparing(Reach::node));
        var distances = new HashMap<String, Double>();
        var signals = new HashMap<String, Observation>();
        var speeds = new HashMap<String, SimulationTraffic.SpeedObservation>();
        if (activeEdge != null && traffic.caps().containsKey(activeEdge.id())) {
            speeds.put(activeEdge.id(), traffic.observeSpeed(activeEdge));
        }
        if (meters <= settings.observationRange()) {
            queue.add(new Reach(node, meters));
            distances.put(node, meters);
        }
        while (!queue.isEmpty()) {
            var reach = queue.remove();
            if (reach.meters() > distances.get(reach.node())) continue;
            var observation = readVisibleSignal.apply(reach.node());
            if (observation != null) signals.put(reach.node(), observation);
            for (var edge : outgoing.getOrDefault(reach.node(), List.of())) {
                if (traffic.caps().containsKey(edge.id())) {
                    speeds.put(edge.id(), traffic.observeSpeed(edge));
                }
                double next = reach.meters() + edge.lengthM();
                if (next > settings.observationRange() || next >= distances.getOrDefault(edge.toNodeId(), Double.POSITIVE_INFINITY)) continue;
                distances.put(edge.toNodeId(), next);
                queue.add(new Reach(edge.toNodeId(), next));
            }
        }
        return new Observations(Map.copyOf(signals), Map.copyOf(speeds));
    }

    List<Edge> chooseSuffix(String start, double arrivalAtStart, boolean includeStartSignal,
                            List<Edge> incumbent, Set<String> visited, Map<String, Observation> signals) {
        return chooseSuffix(start, arrivalAtStart, includeStartSignal, incumbent, visited, new Observations(signals, Map.of()));
    }

    List<Edge> chooseSuffix(String start, double arrivalAtStart, boolean includeStartSignal,
                            List<Edge> incumbent, Set<String> visited, Observations observed) {
        return chooseSuffix(start, arrivalAtStart, includeStartSignal, incumbent, visited, observed, RoadRules.START);
    }

    List<Edge> chooseSuffix(String start, double arrivalAtStart, boolean includeStartSignal,
                            List<Edge> incumbent, Set<String> visited, Observations observed, RoadRules.Context context) {
        double initialWait = includeStartSignal ? waitingAt(start, arrivalAtStart, observed.signals()) : 0;
        return chooseSuffix(start, new Estimate(arrivalAtStart + initialWait, initialWait, 0, 0),
                incumbent, visited, observed, context);
    }

    List<Edge> chooseSuffix(String start, Estimate initial, List<Edge> incumbent, Set<String> visited,
                            Observations observed, RoadRules.Context context) {
        if (incumbent.isEmpty()) return incumbent;
        var incumbentEstimate = estimate(incumbent, initial, observed, context.previous());
        double lowerBound = freeFlowToDestination.getOrDefault(start, Double.POSITIVE_INFINITY);
        List<Edge> fastest = incumbent;
        if (incumbentEstimate.score() > initial.score() + lowerBound + EPSILON_SECONDS
                || incumbentEstimate.waiting() > initial.waiting() + EPSILON_SECONDS) {
            var timed = search(start, initial, visited, observed, incumbentEstimate, context, Integer.MAX_VALUE);
            if (timed != null) fastest = timed;
        }

        if (!start.equals(cachedStart) || !cachedVisited.equals(visited) || !context.equals(cachedContext)) {
            cachedStart = start;
            cachedContext = context;
            cachedVisited = Set.copyOf(visited);
            cachedAlternatives = structuralAlternatives(fastest, visited, context);
        }
        var candidates = new LinkedHashSet<List<Edge>>();
        candidates.add(incumbent);
        candidates.add(fastest);
        for (var alternative : cachedAlternatives) {
            if (candidates.size() >= ROUTE_LIMIT) break;
            candidates.add(alternative);
        }
        var ranked = candidates.stream()
                .map(route -> new Candidate(route, estimate(route, initial, observed, context.previous())))
                .sorted(Comparator.comparingDouble((Candidate candidate) -> candidate.estimate().score())
                        .thenComparingDouble(candidate -> candidate.estimate().waiting()))
                .toList();
        var selected = ranked.stream()
                .filter(candidate -> canReplace(candidate.estimate(), incumbentEstimate, initial, context))
                .findFirst().orElse(new Candidate(incumbent, incumbentEstimate));
        var ordered = new ArrayList<Candidate>();
        ordered.add(selected);
        ranked.stream().filter(candidate -> !candidate.edges().equals(selected.edges())).forEach(ordered::add);
        evaluated = List.copyOf(ordered);
        return selected.edges();
    }

    private static boolean canReplace(Estimate candidate, Estimate incumbent, Estimate initial, RoadRules.Context context) {
        if (!better(candidate, incumbent)) return false;
        if (context.previous() == null && candidate.maneuvers() <= incumbent.maneuvers()
                && candidate.congestion() <= incumbent.congestion() + EPSILON_SECONDS) return true;
        double margin = Math.max(5, 0.05 * (incumbent.score() - initial.score()));
        return incumbent.score() - candidate.score() >= margin;
    }

    List<Candidate> evaluated() { return evaluated; }

    int candidatesEvaluated() { return evaluated.size(); }

    void resetCandidates() { evaluated = List.of(); }

    private List<List<Edge>> structuralAlternatives(List<Edge> route, Set<String> visited, RoadRules.Context context) {
        var initialIncoming = context.previous();
        var alternatives = new LinkedHashSet<List<Edge>>();
        var blocked = new HashSet<>(visited);
        double distance = 0;
        int attempts = 0;
        for (int i = 0; i < route.size() && distance <= settings.observationRange() && attempts < SPUR_SEARCH_LIMIT; i++) {
            var current = route.get(i);
            blocked.add(current.fromNodeId());
            for (var branch : outgoing.getOrDefault(current.fromNodeId(), List.of())) {
                if (branch.equals(current) || blocked.contains(branch.toNodeId())
                        || !freeFlowToDestination.containsKey(branch.toNodeId())) continue;
                var branchContext = rules.enter(context, branch);
                if (branchContext == null) continue;
                if (attempts >= SPUR_SEARCH_LIMIT) break;
                attempts++;
                double ceiling = freeFlowToDestination.get(branch.toNodeId()) + SPUR_EXTRA_SECONDS;
                var tail = search(branch.toNodeId(), new Estimate(0, 0, 0, 0), blocked, NO_OBSERVATIONS,
                        new Estimate(ceiling, Double.POSITIVE_INFINITY, 0, 0), branchContext, SPUR_VISIT_LIMIT);
                if (tail == null) continue;
                var variant = new ArrayList<>(route.subList(0, i));
                variant.add(branch);
                variant.addAll(tail);
                alternatives.add(List.copyOf(variant));
            }
            distance += current.lengthM();
            context = rules.enter(context, current);
        }
        return alternatives.stream()
                .sorted(Comparator.comparingDouble(candidate ->
                        estimate(candidate, new Estimate(0, 0, 0, 0), NO_OBSERVATIONS, initialIncoming).score()))
                .limit(ROUTE_LIMIT - 1L)
                .toList();
    }

    private Estimate estimate(List<Edge> route, Estimate initial, Observations observed, Edge previous) {
        var result = initial;
        for (var edge : route) {
            result = traverse(result, previous, edge, observed);
            previous = edge;
        }
        return result;
    }

    private List<Edge> search(String start, Estimate initial, Set<String> visited,
                              Observations observed, Estimate bestDestination, RoadRules.Context startContext, int visitLimit) {
        double lowerBound = freeFlowToDestination.getOrDefault(start, Double.POSITIVE_INFINITY);
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::lowerBound)
                .thenComparingDouble(visit -> visit.estimate().waiting()).thenComparing(Visit::node));
        var bestByState = new HashMap<SearchKey, Visit>();
        var origin = new Visit(start, initial, initial.score() + lowerBound, null, null, startContext);
        bestByState.put(key(startContext), origin);
        queue.add(origin);
        Visit winner = null;
        int visitedStates = 0;
        while (!queue.isEmpty()) {
            var visit = queue.remove();
            if (visit.lowerBound() > bestDestination.score() + EPSILON_SECONDS) break;
            if (bestByState.get(key(visit.context())) != visit) continue;
            if (visitedStates++ >= visitLimit) break;
            if (visit.node().equals(destination)) {
                if (better(visit.estimate(), bestDestination)) {
                    winner = visit;
                    bestDestination = visit.estimate();
                }
                continue;
            }
            for (var edge : outgoing.getOrDefault(visit.node(), List.of())) {
                String next = edge.toNodeId();
                Double bound = freeFlowToDestination.get(next);
                if (bound == null || visited.contains(next)) continue;
                var context = rules.enter(visit.context(), edge);
                if (context == null || revisits(visit, next)) continue;
                var estimate = traverse(visit.estimate(), visit.context().previous(), edge, observed);
                double projected = estimate.score() + bound;
                if (projected > bestDestination.score() + EPSILON_SECONDS) continue;
                var key = key(context);
                var previous = bestByState.get(key);
                if (previous == null || better(estimate, previous.estimate())) {
                    var candidate = new Visit(next, estimate, projected, visit, edge, context);
                    bestByState.put(key, candidate);
                    queue.add(candidate);
                }
            }
        }
        if (winner == null) return null;
        var reversed = new ArrayList<Edge>();
        for (var visit = winner; visit.edge() != null; visit = visit.previous()) reversed.add(visit.edge());
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static SearchKey key(RoadRules.Context context) {
        return new SearchKey(context.previous() == null ? null : context.previous().id(), context.active(), context.enteredRoundabouts());
    }

    private static boolean revisits(Visit visit, String node) {
        for (var prior = visit; prior != null; prior = prior.previous()) {
            if (prior.node().equals(node)) return true;
        }
        return false;
    }

    private Estimate traverse(Estimate from, Edge previous, Edge edge, Observations observed) {
        return traverse(from, previous, edge, edge.lengthM(), true, observed);
    }

    Estimate traverse(Estimate from, Edge previous, Edge edge, double meters, boolean includeSignal, Observations observed) {
        double maneuver = RoadRules.maneuverDelay(previous, edge);
        double arrival = from.arrival() + maneuver + observed.travelSeconds(edge, meters, settings.maxSpeed());
        double waiting = includeSignal ? waitingAt(edge.toNodeId(), arrival, observed.signals()) : 0;
        return new Estimate(arrival + waiting, from.waiting() + waiting,
                from.congestion() + observed.congestionDelay(edge, meters, settings.maxSpeed()),
                from.maneuvers() + (maneuver > 0 ? 1 : 0));
    }

    private double waitingAt(String node, double arrival, Map<String, Observation> signals) {
        var signal = signals.get(node);
        return signal == null || node.equals(destination) ? 0 : signal.waitSeconds(arrival);
    }

    private static boolean better(Estimate candidate, Estimate previous) {
        double difference = candidate.score() - previous.score();
        return difference < -EPSILON_SECONDS
                || (Math.abs(difference) <= EPSILON_SECONDS && candidate.waiting() < previous.waiting() - EPSILON_SECONDS);
    }

    private Map<String, Double> computeFreeFlowBounds(List<Edge> edges) {
        var incoming = edges.stream().collect(Collectors.groupingBy(Edge::toNodeId));
        var queue = new PriorityQueue<>(Comparator.comparingDouble(FreeFlow::seconds));
        var bounds = new HashMap<String, Double>();
        bounds.put(destination, 0.0);
        queue.add(new FreeFlow(destination, 0));
        while (!queue.isEmpty()) {
            var current = queue.remove();
            if (current.seconds() > bounds.get(current.node())) continue;
            for (var edge : incoming.getOrDefault(current.node(), List.of())) {
                double seconds = current.seconds() + travelSeconds(edge);
                if (seconds >= bounds.getOrDefault(edge.fromNodeId(), Double.POSITIVE_INFINITY)) continue;
                bounds.put(edge.fromNodeId(), seconds);
                queue.add(new FreeFlow(edge.fromNodeId(), seconds));
            }
        }
        return bounds;
    }

    private double travelSeconds(Edge edge) {
        return RoadRoute.freeFlowSeconds(edge, settings.maxSpeed());
    }

}
