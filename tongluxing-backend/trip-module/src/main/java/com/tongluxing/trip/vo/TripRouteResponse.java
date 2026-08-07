package com.tongluxing.trip.vo;

import java.util.List;

/** 正式行程的完整路线响应。仅导航/路线全屏页按需请求，列表和推荐接口不会携带完整折线。 */
public record TripRouteResponse(
        String tripId,
        String routePlanId,
        LocationResponse startLocation,
        LocationResponse destination,
        List<WaypointLocationResponse> waypoints,
        String polyline,
        Integer routeDistance,
        Integer routeDuration,
        String providerType,
        String status
) { }
