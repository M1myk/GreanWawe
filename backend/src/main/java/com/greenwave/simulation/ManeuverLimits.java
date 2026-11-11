package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;

final class ManeuverLimits {
    static final double ZONE_M = 2;
    private ManeuverLimits() {}

    static double speedMps(Edge incoming, Edge outgoing, boolean junction) {
        var before = incoming.geometry().getFirst();
        var vertex = incoming.geometry().getLast();
        var after = outgoing.geometry().getLast();
        double longitudeScale = Math.cos(Math.toRadians(vertex.get(1)));
        double x = (vertex.get(0) - before.get(0)) * longitudeScale;
        double y = vertex.get(1) - before.get(1);
        double nextX = (after.get(0) - vertex.get(0)) * longitudeScale;
        double nextY = after.get(1) - vertex.get(1);
        double angle = Math.toDegrees(Math.abs(Math.atan2(x * nextY - y * nextX, x * nextX + y * nextY)));
        if (angle >= 60) return 15 / 3.6;
        if (angle >= 30) return 20 / 3.6;
        return junction ? 30 / 3.6 : Double.POSITIVE_INFINITY;
    }
}
