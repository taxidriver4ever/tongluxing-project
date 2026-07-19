package com.tongluxing.trip.vo;

import java.util.List;

/** 草稿路线规划快照。 */
public record TripDraftRouteResponse(
        String draftId, String routePlanId, LocationResponse startLocation, LocationResponse destination,
        List<TripCreationWaypointResponse> waypoints, String polyline, Integer totalDistance,
        Integer estimatedDuration, String providerType, String status
) { }
