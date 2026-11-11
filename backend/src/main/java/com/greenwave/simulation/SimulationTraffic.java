package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;
import com.greenwave.network.NetworkController.Network;
import com.greenwave.trip.domain.TrafficMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

record SimulationTraffic(Map<String, Double> caps, Map<String, QueueWave> queues) {
    static final SimulationTraffic EMPTY = new SimulationTraffic(Map.of());

    SimulationTraffic(Map<String, Double> caps) { this(caps, Map.of()); }

    SimulationTraffic {
        caps = Map.copyOf(caps);
        queues = Map.copyOf(queues);
    }

    static SimulationTraffic create(Network network, TrafficMode mode, long seed) {
        if (mode == TrafficMode.NONE) return EMPTY;
        var caps = new HashMap<String, Double>();
        var queues = new HashMap<String, QueueWave>();
        var buckets = new HashMap<String, Integer>();
        boolean synthetic = network.id().equals("synthetic-v1");
        for (var edge : network.edges()) {
            String corridor = synthetic ? network.id() : edge.name();
            int bucket;
            if (synthetic) {
                if (!edge.id().equals("e1") && !edge.id().equals("e2")) continue;
                bucket = 0;
            } else {
                if (corridor.isBlank() || edge.speedLimitMps() < 39 / 3.6) continue;
                bucket = buckets.computeIfAbsent(corridor, name -> random(seed, name).nextInt(100));
            }
            int offset = random(seed ^ 0xDA942042E4DD58B5L, corridor).nextInt(160);
            QueueWave wave = switch (mode) {
                case NONE -> null;
                case NORMAL -> bucket < 25 ? new QueueWave(20 / 3.6, 90, 20, offset, false) : null;
                case RUSH_HOUR -> bucket < 25 ? new QueueWave(10 / 3.6, 60, 60, offset, true)
                        : bucket < 60 ? new QueueWave(14 / 3.6, 80, 40, offset, false) : null;
            };
            if (wave != null) {
                caps.put(edge.id(), Math.min(wave.speedMps(), edge.speedLimitMps()));
                queues.put(edge.id(), wave);
            }
        }
        return new SimulationTraffic(caps, queues);
    }

    private static Random random(long seed, String corridor) {
        return new Random(seed ^ (corridor.hashCode() * 0x9E3779B97F4A7C15L));
    }

    double limit(Edge edge) { return caps.getOrDefault(edge.id(), Double.POSITIVE_INFINITY); }

    double expectedSpeed(Edge edge) {
        var wave = queues.get(edge.id());
        return wave == null ? limit(edge) : wave.expectedSpeed(limit(edge));
    }

    double targetSpeed(Edge edge, int tick) {
        var wave = queues.get(edge.id());
        return wave == null ? limit(edge) : limit(edge) * wave.factorAt(tick);
    }

    boolean hasQueue(Edge edge) { return queues.containsKey(edge.id()); }

    List<Segment> segments(Network network) {
        return network.edges().stream().filter(edge -> caps.containsKey(edge.id()))
                .map(edge -> new Segment(edge.id(), expectedSpeed(edge) / edge.speedLimitMps(), hasQueue(edge) && queues.get(edge.id()).heavy()))
                .toList();
    }

    record Segment(String edgeId, double speedFactor, boolean heavy) {}

    record QueueWave(double speedMps, int movingTicks, int stoppedTicks, int offsetTicks, boolean heavy) {
        private static final int RAMP_TICKS = 20;

        private int cycleTicks() { return movingTicks + stoppedTicks + 2 * RAMP_TICKS; }

        double expectedSpeed(double cap) {
            return cap * (movingTicks + RAMP_TICKS) / cycleTicks();
        }

        double factorAt(int tick) {
            int phase = Math.floorMod(tick + offsetTicks, cycleTicks());
            if (phase < movingTicks) return 1;
            phase -= movingTicks;
            if (phase < RAMP_TICKS) return 1 - (phase + 1.0) / RAMP_TICKS;
            phase -= RAMP_TICKS;
            if (phase < stoppedTicks) return 0;
            return (phase - stoppedTicks + 1.0) / RAMP_TICKS;
        }
    }
}
