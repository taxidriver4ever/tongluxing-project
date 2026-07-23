package com.tongluxing.match.vo;

import java.util.List;

/** 未入队用户可查看的公开行程详情，不包含群聊消息和成员隐私。 */
public record TripSearchDetailResponse(
        String tripId,
        String ownerUserId,
        String ownerNickname,
        String ownerAvatarImageKey,
        String title,
        String description,
        String startName,
        String endName,
        List<String> waypoints,
        String departureTime,
        String estimatedEndTime,
        Integer routeDistanceMeters,
        Integer routeDurationSeconds,
        String teamId,
        String teamName,
        String announcement,
        String vehicleSummary,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Integer remainingSeats,
        String joinRequirement,
        String status,
        Boolean joinable
) {
}
