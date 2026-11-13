package com.greenwave.simulation;

import com.greenwave.shared.api.ApiException;
import com.greenwave.trip.api.dto.TripConfigRequest;

record SimulationSettings(double maxSpeed, double acceleration, double braking,
                          double observationRange, int decisionTicks, int maxTicks) {
    static final SimulationSettings DEFAULT = new SimulationSettings(50 / 3.6, 2, 3, 1500, 10, 30_000);

    static SimulationSettings from(TripConfigRequest config) {
        double interval = config.navigation().decisionIntervalSeconds() / BaselineSimulation.STEP;
        if (Math.abs(interval - Math.rint(interval)) > 1e-8) {
            throw ApiException.invalid("navigation.decisionIntervalSeconds", "Must be a multiple of 0.1 seconds");
        }
        double braking = config.vehicle().brakingMps2();
        double maxSpeed = config.vehicle().maxSpeedMps();
        int maxTicks = (int) Math.floor(config.maxDurationSeconds() / BaselineSimulation.STEP + 1e-8);
        return new SimulationSettings(maxSpeed, config.vehicle().accelerationMps2(), braking,
                config.navigation().observationRangeM(), (int) Math.round(interval), maxTicks);
    }
}
