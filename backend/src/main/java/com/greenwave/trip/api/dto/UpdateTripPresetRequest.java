package com.greenwave.trip.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateTripPresetRequest(@NotBlank String name,
                                      @NotNull @Valid TripConfigRequest config,
                                      @NotNull @Min(1) Long version) { }
