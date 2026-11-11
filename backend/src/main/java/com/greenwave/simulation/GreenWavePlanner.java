package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.RoadRoute;
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

    record Observations(Map<String, Observation> signals, Map<String, Double> trafficCaps) {
        double speed(Edge edge, double vehicleMax) {
            return Math.min(Math.min(vehicleMax, edge.speedLimitMps()),
                    trafficCaps.getOrDefault(edge.id(), Double.POSITIVE_INFINITY));
        }
    }
    private static final Observations NO_OBSERVATIONS = new Observations(Map.of(), Map.of());

    private record Reach(String node, double meters) {}
    private record FreeFlow(String node, double seconds) {}
    private record Estimate(double arrival, double waiting) {}
    private record Visit(String node, Estimate estimate, double lowerBound, Visit previous, Edge edge) {}

    private final SimulationSettings settings;
    private final String destination;
    private final Map<String, List<Edge>> outgoing;
    private final Map<String, Double> freeFlowToDestination;
    private String cachedStart;
    private Set<String> cachedVisited = Set.of();
    private List<List<Edge>> cachedAlternatives = List.of();
    private int candidatesEvaluated;

    GreenWavePlanner(Network network, String destination, SimulationSettings settings) {
        this.settings = settings;
        this.destination = destination;
        outgoing = network.edges().stream().collect(Collectors.groupingBy(Edge::fromNodeId));
        freeFlowToDestination = computeFreeFlowBounds(network.edges());
    }

    Map<String, Observation> observe(String node, double meters, Function<String, Observation> readVisibleSignal) {
        return observe(node, meters, readVisibleSignal, SimulationTraffic.EMPTY).signals();
    }

    Observations observe(String node, double meters, Function<String, Observation> readVisibleSignal, SimulationTraffic traffic) {
        if (meters > settings.observationRange()) return NO_OBSERVATIONS;
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Reach::meters).thenComparing(Reach::node));
        var distances = new HashMap<String, Double>();
        var signals = new HashMap<String, Observation>();
        var trafficCaps = new HashMap<String, Double>();
        queue.add(new Reach(node, meters));
        distances.put(node, meters);
        while (!queue.isEmpty()) {
            var reach = queue.remove();
            if (reach.meters() > distances.get(reach.node())) continue;
            var observation = readVisibleSignal.apply(reach.node());
            if (observation != null) signals.put(reach.node(), observation);
            for (var edge : outgoing.getOrDefault(reach.node(), List.of())) {
                Double cap = traffic.caps().get(edge.id());
                if (cap != null) trafficCaps.put(edge.id(), cap);
                double next = reach.meters() + edge.lengthM();
                if (next > settings.observationRange() || next >= distances.getOrDefault(edge.toNodeId(), Double.POSITIVE_INFINITY)) continue;
                distances.put(edge.toNodeId(), next);
                queue.add(new Reach(edge.toNodeId(), next));
            }
        }
        return new Observations(signals, trafficCaps);
    }

    List<Edge> chooseSuffix(String start, double arrivalAtStart, boolean includeStartSignal,
                            List<Edge> incumbent, Set<String> visited, Map<String, Observation> signals) {
        return chooseSuffix(start, arrivalAtStart, includeStartSignal, incumbent, visited, new Observations(signals, Map.of()));
    }

    List<Edge> chooseSuffix(String start, double arrivalAtStart, boolean includeStartSignal,
                            List<Edge> incumbent, Set<String> visited, Observations observed) {
        if (incumbent.isEmpty()) return incumbent;
        double initialWait = includeStartSignal ? waitingAt(start, arrivalAtStart, observed.signals()) : 0;
        var initial = new Estimate(arrivalAtStart + initialWait, initialWait);
        var incumbentEstimate = estimate(incumbent, initial, observed);
        double lowerBound = freeFlowToDestination.getOrDefault(start, Double.POSITIVE_INFINITY);
        List<Edge> fastest = incumbent;
        if (incumbentEstimate.arrival() > initial.arrival() + lowerBound + EPSILON_SECONDS
                || incumbentEstimate.waiting() > initial.waiting() + EPSILON_SECONDS) {
            var timed = search(start, initial, visited, observed, incumbentEstimate);
            if (timed != null) fastest = timed;
        }

        if (!start.equals(cachedStart) || !cachedVisited.equals(visited)) {
            cachedStart = start;
            cachedVisited = Set.copyOf(visited);
            cachedAlternatives = structuralAlternatives(fastest, visited);
        }
        var candidates = new LinkedHashSet<List<Edge>>();
        candidates.add(incumbent);
        candidates.add(fastest);
        for (var alternative : cachedAlternatives) {
            if (candidates.size() >= ROUTE_LIMIT) break;
            candidates.add(alternative);
        }
        candidatesEvaluated = candidates.size();
        var selected = incumbent;
        var best = incumbentEstimate;
        for (var candidate : candidates) {
            var projected = estimate(candidate, initial, observed);
            if (better(projected, best)) {
                selected = candidate;
                best = projected;
            }
        }
        return selected;
    }

    int candidatesEvaluated() { return candidatesEvaluated; }

    void resetCandidates() { candidatesEvaluated = 0; }

    private List<List<Edge>> structuralAlternatives(List<Edge> route, Set<String> visited) {
        var alternatives = new LinkedHashSet<List<Edge>>();
        var blocked = new HashSet<>(visited);
        double distance = 0;
        int attempts = 0;
        for (int i = 0; i < route.size() && distance <= settings.observationRange(); i++) {
            var current = route.get(i);
            blocked.add(current.fromNodeId());
            for (var branch : outgoing.getOrDefault(current.fromNodeId(), List.of())) {
                if (branch.equals(current) || blocked.contains(branch.toNodeId())
                        || !freeFlowToDestination.containsKey(branch.toNodeId())) continue;
                if (attempts++ >= SPUR_SEARCH_LIMIT || alternatives.size() >= ROUTE_LIMIT - 1) {
                    return List.copyOf(alternatives);
                }
                var tail = search(branch.toNodeId(), new Estimate(0, 0), blocked, NO_OBSERVATIONS,
                        new Estimate(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
                if (tail == null) continue;
                var variant = new ArrayList<>(route.subList(0, i));
                variant.add(branch);
                variant.addAll(tail);
                alternatives.add(List.copyOf(variant));
            }
            distance += current.lengthM();
        }
        return List.copyOf(alternatives);
    }

    private Estimate estimate(List<Edge> route, Estimate initial, Observations observed) {
        var result = initial;
        for (var edge : route) result = traverse(result, edge, observed);
        return result;
    }

    private List<Edge> search(String start, Estimate initial, Set<String> visited,
                              Observations observed, Estimate bestDestination) {
        double lowerBound = freeFlowToDestination.getOrDefault(start, Double.POSITIVE_INFINITY);
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::lowerBound)
                .thenComparingDouble(visit -> visit.estimate().waiting()).thenComparing(Visit::node));
        var bestByNode = new HashMap<String, Visit>();
        var origin = new Visit(start, initial, initial.arrival() + lowerBound, null, null);
        bestByNode.put(start, origin);
        queue.add(origin);
        Visit winner = null;
        while (!queue.isEmpty()) {
            var visit = queue.remove();
            if (visit.lowerBound() > bestDestination.arrival() + EPSILON_SECONDS) break;
            if (bestByNode.get(visit.node()) != visit) continue;
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
                var estimate = traverse(visit.estimate(), edge, observed);
                double projected = estimate.arrival() + bound;
                if (projected > bestDestination.arrival() + EPSILON_SECONDS) continue;
                var previous = bestByNode.get(next);
                if (previous == null || better(estimate, previous.estimate())) {
                    var candidate = new Visit(next, estimate, projected, visit, edge);
                    bestByNode.put(next, candidate);
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

    private Estimate traverse(Estimate from, Edge edge, Observations observed) {
        double arrival = from.arrival() + edge.lengthM() / observed.speed(edge, settings.maxSpeed());
        double waiting = waitingAt(edge.toNodeId(), arrival, observed.signals());
        return new Estimate(arrival + waiting, from.waiting() + waiting);
    }

    private double waitingAt(String node, double arrival, Map<String, Observation> signals) {
        var signal = signals.get(node);
        return signal == null || node.equals(destination) ? 0 : signal.waitSeconds(arrival);
    }

    private static boolean better(Estimate candidate, Estimate previous) {
        double difference = candidate.arrival() - previous.arrival();
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
