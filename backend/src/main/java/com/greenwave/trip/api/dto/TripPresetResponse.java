package com.greenwave.trip.api.dto;

import java.time.Instant;

public record TripPresetResponse(String id, String name, TripConfigResponse config, long version,
                                 Instant createdAt, Instant updatedAt) { }
