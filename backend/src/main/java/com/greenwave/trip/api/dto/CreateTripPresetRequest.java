package com.greenwave.trip.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateTripPresetRequest(@NotBlank String name,
                                      @NotNull @Valid TripConfigRequest config) { }
