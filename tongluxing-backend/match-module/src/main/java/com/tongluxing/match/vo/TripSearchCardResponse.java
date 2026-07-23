package com.tongluxing.match.vo;

import java.util.List;

/** 搜索结果中的公开行程卡片。 */
public record TripSearchCardResponse(
        String tripId,
        String ownerUserId,
        String ownerNickname,
        String ownerAvatarImageKey,
        String title,
        String startName,
        String endName,
        List<String> waypoints,
        String departureTime,
        String estimatedEndTime,
        String vehicleSummary,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        Integer matchScore,
        Integer startDistanceMeters,
        Integer endDistanceMeters,
        String status,
        Boolean joinable
) {
}
