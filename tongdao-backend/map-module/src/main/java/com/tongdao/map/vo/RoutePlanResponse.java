package com.tongdao.map.vo;

import java.util.List;

import com.tongdao.map.dto.LocationDto;

/**
 * 路线规划响应。
 */
public record RoutePlanResponse(
        /** 路线规划 ID。 */
        String routePlanId,
        /** 路线距离，单位米。 */
        Integer routeDistance,
        /** 路线预计耗时，单位秒。 */
        Integer routeDuration,
        /** 路线折线或 MOCK 路线点 JSON。 */
        String routePolyline,
        /** 路线点列表，包含起点、途经点和终点。 */
        List<LocationDto> routePoints,
        /** 地图服务商类型。 */
        String providerType,
        /** 规划状态。 */
        String planStatus
) {
}
