package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.trip.domain.TrafficMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

record SimulationTraffic(Map<String, Double> caps) {
    static final SimulationTraffic EMPTY = new SimulationTraffic(Map.of());

    SimulationTraffic { caps = Map.copyOf(caps); }

    static SimulationTraffic create(Network network, TrafficMode mode, long seed) {
        if (mode == TrafficMode.NONE) return EMPTY;
        var caps = new HashMap<String, Double>();
        var buckets = new HashMap<String, Integer>();
        boolean synthetic = network.id().equals("synthetic-v1");
        for (var edge : network.edges()) {
            int bucket;
            if (synthetic) {
                if (!edge.id().equals("e1") && !edge.id().equals("e2")) continue;
                bucket = 0;
            } else {
                if (edge.name().isBlank() || edge.speedLimitMps() < 39 / 3.6) continue;
                bucket = buckets.computeIfAbsent(edge.name(), name ->
                        new Random(seed ^ (name.hashCode() * 0x9E3779B97F4A7C15L)).nextInt(100));
            }
            double factor = switch (mode) {
                case NONE -> 1;
                case NORMAL -> bucket < 25 ? 0.70 : 1;
                case RUSH_HOUR -> bucket < 25 ? 0.45 : bucket < 60 ? 0.60 : 1;
            };
            if (factor < 1) caps.put(edge.id(), edge.speedLimitMps() * factor);
        }
        return new SimulationTraffic(caps);
    }

    double limit(Edge edge) { return caps.getOrDefault(edge.id(), Double.POSITIVE_INFINITY); }

    List<Segment> segments(Network network) {
        return network.edges().stream().filter(edge -> caps.containsKey(edge.id()))
                .map(edge -> new Segment(edge.id(), limit(edge) / edge.speedLimitMps())).toList();
    }

    record Segment(String edgeId, double speedFactor) {}
}
