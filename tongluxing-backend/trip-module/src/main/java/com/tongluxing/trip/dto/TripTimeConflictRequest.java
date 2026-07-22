package com.tongluxing.trip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 发布或编辑行程前的预计时间冲突检查。 */
public record TripTimeConflictRequest(
        @NotBlank String departureTime,
        @Min(1) @Max(365) Integer estimatedDays,
        Long excludeTripId
) {
}
