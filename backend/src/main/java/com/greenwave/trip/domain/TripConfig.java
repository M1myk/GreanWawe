package com.greenwave.trip.domain;

public record TripConfig(String scenarioId, String startNodeId, String destinationNodeId,
                         long randomSeed, Vehicle vehicle, Navigation navigation, double maxDurationSeconds) {
    public record Vehicle(double maxSpeedMps, double accelerationMps2, double brakingMps2) { }
    public record Navigation(double observationRangeM, double decisionIntervalSeconds) { }
}
