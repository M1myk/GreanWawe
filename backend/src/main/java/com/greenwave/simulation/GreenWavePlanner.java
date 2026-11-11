package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.network.RoadRoute;
import com.greenwave.shared.api.ApiException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

final class GreenWavePlanner {
    static final double RANGE_M = 500;
    static final int DECISION_TICKS = 10;
    private static final double SWITCH_SAVINGS_SECONDS = 2;
    private static final double MAX_SPEED = BaselineSimulation.MAX_SPEED;

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

    private record Reach(String node, double meters, double seconds) {}
    record VisibleSignal(Observation state, double freeFlowArrivalSeconds) {}

    private final Network network;
    private final String destination;
    private final Map<String, List<Edge>> outgoing;

    GreenWavePlanner(Network network, String destination) {
        this.network = network;
        this.destination = destination;
        outgoing = network.edges().stream().collect(Collectors.groupingBy(Edge::fromNodeId));
    }

    Map<String, VisibleSignal> observe(String node, double meters, double seconds,
                                       Function<String, Observation> readVisibleSignal) {
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Reach::meters)
                .thenComparingDouble(Reach::seconds).thenComparing(Reach::node));
        var distances = new HashMap<String, Double>();
        var signals = new HashMap<String, VisibleSignal>();
        if (meters > RANGE_M) return signals;
        queue.add(new Reach(node, meters, seconds));
        distances.put(node, meters);
        while (!queue.isEmpty()) {
            var reach = queue.remove();
            if (reach.meters() > distances.get(reach.node())) continue;
            var observation = readVisibleSignal.apply(reach.node());
            if (observation != null) signals.put(reach.node(), new VisibleSignal(observation, reach.seconds()));
            for (var edge : outgoing.getOrDefault(reach.node(), List.of())) {
                double next = reach.meters() + edge.lengthM();
                if (next > RANGE_M || next >= distances.getOrDefault(edge.toNodeId(), Double.POSITIVE_INFINITY)) continue;
                distances.put(edge.toNodeId(), next);
                queue.add(new Reach(edge.toNodeId(), next, reach.seconds() + RoadRoute.freeFlowSeconds(edge, MAX_SPEED)));
            }
        }
        return Map.copyOf(signals);
    }

    List<Edge> chooseSuffix(String start, List<Edge> incumbent, Set<String> visited,
                            Map<String, VisibleSignal> signals) {
        if (incumbent.isEmpty()) return incumbent;
        var penalties = new HashMap<String, Double>();
        signals.forEach((id, signal) -> penalties.put(id, signal.state().waitSeconds(signal.freeFlowArrivalSeconds())));
        if (incumbent.stream().noneMatch(edge -> !edge.toNodeId().equals(destination)
                && penalties.getOrDefault(edge.toNodeId(), 0.0) > 0)) return incumbent;

        ToDoubleFunction<Edge> penalty = edge -> edge.toNodeId().equals(destination)
                ? 0 : penalties.getOrDefault(edge.toNodeId(), 0.0);
        List<Edge> alternative;
        try {
            alternative = RoadRoute.findEdges(network, start, destination, MAX_SPEED, edge ->
                    visited.contains(edge.toNodeId()) ? Double.POSITIVE_INFINITY : penalty.applyAsDouble(edge));
        } catch (ApiException exception) {
            if (exception.status() == 422) return incumbent;
            throw exception;
        }
        if (cost(incumbent, penalty) - cost(alternative, penalty) > SWITCH_SAVINGS_SECONDS) return alternative;
        return incumbent;
    }

    private static double cost(List<Edge> edges, ToDoubleFunction<Edge> penalty) {
        return edges.stream().mapToDouble(edge -> RoadRoute.freeFlowSeconds(edge, MAX_SPEED) + penalty.applyAsDouble(edge)).sum();
    }

    static double chooseSpeed(double current, Observation signal, DoubleUnaryOperator crossingTicks) {
        double best = MAX_SPEED;
        double bestArrival = Double.POSITIVE_INFINITY;
        for (double candidate = MAX_SPEED; candidate >= 2; candidate = candidate == MAX_SPEED ? Math.ceil(MAX_SPEED) - 1 : candidate - 1) {
            double arrival = crossingTicks.applyAsDouble(candidate);
            if (!Double.isFinite(arrival) || signal.waitSeconds(arrival * BaselineSimulation.STEP) > 0) continue;
            if (arrival < bestArrival || (arrival == bestArrival && Math.abs(candidate - current) < Math.abs(best - current))) {
                best = candidate;
                bestArrival = arrival;
            }
        }
        return best;
    }
}
