package com.tongdao.map.vo;

import java.util.List;

import com.tongdao.map.dto.LocationDto;

public record RoutePlanResponse(
        String routePlanId,
        Integer routeDistance,
        Integer routeDuration,
        String routePolyline,
        List<LocationDto> routePoints,
        String providerType,
        String planStatus
) {
}
