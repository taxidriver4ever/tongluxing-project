package com.tongluxing.map.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 路线规划请求。
 */
public record RoutePlanRequest(
        /** 起点。 */
        @Valid @NotNull LocationDto startLocation,
        /** 终点。 */
        @Valid @NotNull LocationDto endLocation,
        /** 途经点，当前最多 5 个。 */
        @Valid @Size(max = 5) List<LocationDto> waypoints
) {
}
