package com.greenwave.trip.api.dto;

import java.util.List;

public record TripPresetPage(List<TripPresetResponse> items, long total, int limit, int offset) { }
