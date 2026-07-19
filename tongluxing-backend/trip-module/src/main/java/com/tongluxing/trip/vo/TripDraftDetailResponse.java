package com.tongluxing.trip.vo;

import java.util.List;

/** 创建行程草稿详情。 */
public record TripDraftDetailResponse(
        String draftId, String title, String startTime, LocationResponse startLocation,
        LocationResponse destination, String description, Integer expectPeople, Integer durationDays,
        String status, String publishedTripId, List<TripCreationWaypointResponse> waypoints,
        TripDraftRouteResponse route, String updatedAt
) { }
