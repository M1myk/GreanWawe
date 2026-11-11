package com.greenwave.trip.api.dto;

import com.greenwave.trip.domain.TrafficMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record TripConfigRequest(
        @NotBlank String scenarioId,
        @NotBlank String startNodeId,
        @NotBlank String destinationNodeId,
        @NotNull @Min(0) @Max(2147483647) Long randomSeed,
        @NotNull @Valid Vehicle vehicle,
        @NotNull @Valid Navigation navigation,
        @NotNull @DecimalMin("10") @DecimalMax("7200") Double maxDurationSeconds, TrafficMode trafficMode) {
    public TripConfigRequest {
        if (trafficMode == null) trafficMode = TrafficMode.NONE;
    }

    public record Vehicle(
            @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("40") Double maxSpeedMps,
            @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("10") Double accelerationMps2,
            @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("10") Double brakingMps2) { }
    public record Navigation(
            @NotNull @DecimalMin("50") @DecimalMax("2000") Double observationRangeM,
            @NotNull @DecimalMin("0.1") @DecimalMax("10") Double decisionIntervalSeconds) { }
}
