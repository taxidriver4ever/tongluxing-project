package com.tongluxing.map.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 路线规划请求。
 *
 * <p>路线点顺序固定为起点、途经点列表、终点；该顺序同时影响高德选路和缓存 hash。</p>
 */
public record RoutePlanRequest(
        /** 起点。 */
        @Valid @NotNull LocationDto startLocation,
        /** 终点。 */
        @Valid @NotNull LocationDto endLocation,
        /** 途经点，V1.7 最多 20 个。 */
        @Valid @Size(max = 20) List<LocationDto> waypoints
) {
}
