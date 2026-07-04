package com.tongluxing.trip.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * WaypointLocationRequest 请求参数对象。
 */
public record WaypointLocationRequest(
        @Size(max = 128) String name,
        @Size(max = 255) String address,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        @Min(1) @Max(5) Integer sortOrder
) {
}
