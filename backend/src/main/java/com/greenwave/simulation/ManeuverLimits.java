package com.greenwave.simulation;

import com.greenwave.network.NetworkController.Edge;

final class ManeuverLimits {
    static final double ZONE_M = 2;
    private ManeuverLimits() {}

    static double speedMps(Edge incoming, Edge outgoing, boolean junction) {
        double angle = Math.abs(com.greenwave.network.RoadRules.turnDegrees(incoming, outgoing));
        if (angle >= 60) return 15 / 3.6;
        if (angle >= 30) return 20 / 3.6;
        return junction ? 30 / 3.6 : Double.POSITIVE_INFINITY;
    }
}
