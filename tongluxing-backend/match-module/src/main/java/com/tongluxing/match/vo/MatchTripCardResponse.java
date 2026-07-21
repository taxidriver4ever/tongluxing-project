package com.tongluxing.match.vo;

/**
 * MatchTripCardResponse 响应数据对象。
 */
public record MatchTripCardResponse(
        String matchId,
        String tripId,
        String userId,
        String title,
        String startName,
        String endName,
        String departureTime,
        String travelDepth,
        String status,
        Integer matchScore,
        Integer overlapRate,
        Integer departureGapMinutes,
        Integer distanceGapMeters,
        Integer expectedPeople,
        String teamId,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Boolean joinable
) {
}
