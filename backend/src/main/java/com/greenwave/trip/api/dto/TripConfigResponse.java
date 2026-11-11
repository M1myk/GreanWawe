package com.greenwave.trip.api.dto;

import com.greenwave.trip.domain.TrafficMode;
public record TripConfigResponse(String scenarioId, String startNodeId, String destinationNodeId,
                                 long randomSeed, Vehicle vehicle, Navigation navigation, double maxDurationSeconds, TrafficMode trafficMode) {
    public record Vehicle(double maxSpeedMps, double accelerationMps2, double brakingMps2) { }
    public record Navigation(double observationRangeM, double decisionIntervalSeconds) { }
}
