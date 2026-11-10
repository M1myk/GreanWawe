package com.greenwave.trip.domain;

import java.time.Instant;

public record TripPreset(String id, String name, TripConfig config, long version,
                         Instant createdAt, Instant updatedAt) { }
