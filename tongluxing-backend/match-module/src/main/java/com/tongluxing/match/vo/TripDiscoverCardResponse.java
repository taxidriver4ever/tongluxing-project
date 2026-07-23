package com.tongluxing.match.vo;

import java.util.List;

/** 公共发现信息流中的行程卡片。 */
public record TripDiscoverCardResponse(
        String tripId,
        String title,
        String status,
        String startName,
        List<String> waypoints,
        String endName,
        String departureTime,
        Integer estimatedDays,
        String description,
        Integer joinedVehicleCount,
        Integer maxVehicleCount,
        Integer memberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        Integer matchScore,
        Integer distanceMeters,
        List<String> tags,
        String coverImageKey,
        String relationshipStatus,
        TripDiscoverOwnerResponse owner
) {
}
