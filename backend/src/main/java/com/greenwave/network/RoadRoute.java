package com.greenwave.network;

import com.greenwave.shared.api.ApiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import java.util.function.ToDoubleFunction;

public record RoadRoute(List<List<Double>> geometry, double lengthM) {
    private record Visit(String id, double seconds, double estimate) {}

    static RoadRoute between(NetworkController.Network network, String start, String destination) {
        var edges = findEdges(network, start, destination, Double.POSITIVE_INFINITY);
        if (edges.isEmpty()) {
            var node = network.nodes().stream().filter(candidate -> candidate.id().equals(start)).findFirst().orElseThrow();
            return new RoadRoute(List.of(List.of(node.lon(), node.lat())), 0);
        }
        return fromEdges(edges);
    }

    public static RoadRoute fromEdges(List<NetworkController.Edge> edges) {
        var geometry = new ArrayList<List<Double>>();
        geometry.add(edges.getFirst().geometry().getFirst());
        double length = 0;
        for (var edge : edges) {
            geometry.addAll(edge.geometry().subList(1, edge.geometry().size()));
            length += edge.lengthM();
        }
        return new RoadRoute(List.copyOf(geometry), length);
    }

    public static List<NetworkController.Edge> findEdges(NetworkController.Network network, String start,
                                                         String destination, double vehicleMax) {
        return findEdges(network, start, destination, vehicleMax, edge -> 0);
    }

    public static List<NetworkController.Edge> findEdges(NetworkController.Network network, String start,
                                                         String destination, double vehicleMax,
                                                         ToDoubleFunction<NetworkController.Edge> addedSeconds) {
        var nodes = network.nodes().stream().collect(Collectors.toMap(NetworkController.Node::id, node -> node));
        if (!nodes.containsKey(start) || !nodes.containsKey(destination)) {
            throw new ApiException(404, "NOT_FOUND", "Route endpoint not found in the road network");
        }
        if (start.equals(destination)) return List.of();

        double maxSpeed = Math.min(vehicleMax, network.edges().stream()
                .mapToDouble(NetworkController.Edge::speedLimitMps).max().orElse(vehicleMax));
        double distanceScale = network.edges().stream().mapToDouble(edge -> {
            double straight = straightDistance(nodes.get(edge.fromNodeId()), nodes.get(edge.toNodeId()));
            return straight > 0 ? edge.lengthM() / straight : 1;
        }).min().orElse(1);
        double secondsPerMeter = 0.999 * Math.min(1, distanceScale) / maxSpeed;
        var outgoing = network.edges().stream().collect(Collectors.groupingBy(NetworkController.Edge::fromNodeId));
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::estimate).thenComparing(Visit::id));
        Map<String, Double> costs = new HashMap<>();
        Map<String, NetworkController.Edge> previous = new HashMap<>();
        costs.put(start, 0.0);
        queue.add(new Visit(start, 0, straightDistance(nodes.get(start), nodes.get(destination)) * secondsPerMeter));
        while (!queue.isEmpty()) {
            var visit = queue.remove();
            if (visit.seconds() > costs.get(visit.id())) continue;
            if (visit.id().equals(destination)) {
                var reversed = new ArrayList<NetworkController.Edge>();
                for (String id = destination; !id.equals(start); ) {
                    var edge = previous.get(id);
                    reversed.add(edge);
                    id = edge.fromNodeId();
                }
                Collections.reverse(reversed);
                return List.copyOf(reversed);
            }
            for (var edge : outgoing.getOrDefault(visit.id(), List.of())) {
                double penalty = addedSeconds.applyAsDouble(edge);
                if (!Double.isFinite(penalty)) continue;
                double seconds = visit.seconds() + freeFlowSeconds(edge, vehicleMax) + penalty;
                if (seconds < costs.getOrDefault(edge.toNodeId(), Double.POSITIVE_INFINITY)) {
                    costs.put(edge.toNodeId(), seconds);
                    previous.put(edge.toNodeId(), edge);
                    double estimate = seconds + straightDistance(nodes.get(edge.toNodeId()), nodes.get(destination)) * secondsPerMeter;
                    queue.add(new Visit(edge.toNodeId(), seconds, estimate));
                }
            }
        }
        throw new ApiException(422, "NO_ROUTE", "No directed road route connects these points");
    }

    public static double freeFlowSeconds(NetworkController.Edge edge, double vehicleMax) {
        return edge.lengthM() / Math.min(edge.speedLimitMps(), vehicleMax);
    }

    private static double straightDistance(NetworkController.Node from, NetworkController.Node to) {
        double deltaLat = Math.toRadians(to.lat() - from.lat());
        double deltaLon = Math.toRadians(to.lon() - from.lon());
        double sinLat = Math.sin(deltaLat / 2);
        double sinLon = Math.sin(deltaLon / 2);
        double a = sinLat * sinLat + Math.cos(Math.toRadians(from.lat())) *
                Math.cos(Math.toRadians(to.lat())) * sinLon * sinLon;
        return 12_742_000 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
