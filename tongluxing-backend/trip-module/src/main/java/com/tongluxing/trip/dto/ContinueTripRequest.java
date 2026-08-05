package com.tongluxing.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 到达终点后继续行程并设置新终点。 */
public record ContinueTripRequest(
        @Valid @NotNull LocationRequest endLocation
) {
}
