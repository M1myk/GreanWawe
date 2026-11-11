package com.greenwave.network;

import com.greenwave.network.NetworkController.Edge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RoadRules {
    public static final RoadRules EMPTY = new RoadRules(List.of(), Map.of());
    public static final Context START = new Context(null, List.of(), Set.of());
    private final Map<String, List<List<String>>> starting = new HashMap<>();
    private final Map<String, String> roundabouts;

    public RoadRules(List<List<String>> forbidden, Map<String, String> roundabouts) {
        for (var sequence : forbidden) {
            if (sequence.size() < 2) throw new IllegalArgumentException("A turn rule requires at least two edges.");
            starting.computeIfAbsent(sequence.getFirst(), ignored -> new ArrayList<>()).add(List.copyOf(sequence));
        }
        this.roundabouts = Map.copyOf(roundabouts);
    }

    public record Context(Edge previous, List<Progress> active, Set<String> enteredRoundabouts) {}
    public record Progress(List<String> sequence, int nextIndex) {}

    public Context enter(Context context, Edge edge) {
        var previous = context.previous();
        if (previous != null && (!previous.toNodeId().equals(edge.fromNodeId())
                || previous.fromNodeId().equals(edge.toNodeId()))) return null;
        var entered = context.enteredRoundabouts();
        String group = roundabouts.get(edge.id());
        if (group != null && (previous == null || !group.equals(roundabouts.get(previous.id())))) {
            if (entered.contains(group)) return null;
            var updated = new HashSet<>(entered);
            updated.add(group);
            entered = Set.copyOf(updated);
        }
        var active = new ArrayList<Progress>();
        for (var match : context.active()) {
            if (!match.sequence().get(match.nextIndex()).equals(edge.id())) continue;
            int next = match.nextIndex() + 1;
            if (next == match.sequence().size()) return null;
            active.add(new Progress(match.sequence(), next));
        }
        for (var sequence : starting.getOrDefault(edge.id(), List.of())) active.add(new Progress(sequence, 1));
        return new Context(edge, List.copyOf(active), entered);
    }

    public static double turnDegrees(Edge incoming, Edge outgoing) {
        var before = incoming.geometry().getFirst();
        var vertex = incoming.geometry().getLast();
        var after = outgoing.geometry().getLast();
        double longitudeScale = Math.cos(Math.toRadians(vertex.get(1)));
        double x = (vertex.get(0) - before.get(0)) * longitudeScale;
        double y = vertex.get(1) - before.get(1);
        double nextX = (after.get(0) - vertex.get(0)) * longitudeScale;
        double nextY = after.get(1) - vertex.get(1);
        return Math.toDegrees(Math.atan2(x * nextY - y * nextX, x * nextX + y * nextY));
    }

    public static double maneuverDelay(Edge incoming, Edge outgoing) {
        if (incoming == null) return 0;
        boolean sameNamedRoad = !incoming.name().isBlank() && incoming.name().equals(outgoing.name());
        double angle = turnDegrees(incoming, outgoing);
        double delay = 0;
        if (Math.abs(angle) >= (sameNamedRoad ? 60 : 35)) {
            delay = angle > 0 ? 6 : 3;
            if (Math.abs(angle) >= 90) delay += 2;
        }
        if (!sameNamedRoad && incoming.speedLimitMps() >= 45 / 3.6 && outgoing.speedLimitMps() <= 35 / 3.6) {
            delay += 6;
        }
        return delay;
    }
}
