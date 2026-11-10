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

record RoadRoute(List<List<Double>> geometry, double lengthM) {
    private record Visit(String id, double seconds) {}

    static RoadRoute between(NetworkController.Network network, String start, String destination) {
        var nodes = network.nodes().stream().collect(Collectors.toMap(NetworkController.Node::id, node -> node));
        if (!nodes.containsKey(start) || !nodes.containsKey(destination)) {
            throw new ApiException(404, "NOT_FOUND", "Route endpoint not found in the road network");
        }
        if (start.equals(destination)) {
            var node = nodes.get(start);
            return new RoadRoute(List.of(List.of(node.lon(), node.lat())), 0);
        }
        var outgoing = network.edges().stream().collect(Collectors.groupingBy(NetworkController.Edge::fromNodeId));
        var queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::seconds).thenComparing(Visit::id));
        Map<String, Double> costs = new HashMap<>();
        Map<String, NetworkController.Edge> previous = new HashMap<>();
        costs.put(start, 0.0);
        queue.add(new Visit(start, 0));
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
                var geometry = new ArrayList<List<Double>>();
                geometry.add(reversed.getFirst().geometry().getFirst());
                double length = 0;
                for (var edge : reversed) {
                    geometry.addAll(edge.geometry().subList(1, edge.geometry().size()));
                    length += edge.lengthM();
                }
                return new RoadRoute(geometry, length);
            }
            for (var edge : outgoing.getOrDefault(visit.id(), List.of())) {
                double seconds = visit.seconds() + edge.lengthM() / edge.speedLimitMps();
                if (seconds < costs.getOrDefault(edge.toNodeId(), Double.POSITIVE_INFINITY)) {
                    costs.put(edge.toNodeId(), seconds);
                    previous.put(edge.toNodeId(), edge);
                    queue.add(new Visit(edge.toNodeId(), seconds));
                }
            }
        }
        throw new ApiException(422, "NO_ROUTE", "No directed road route connects these points");
    }
}
