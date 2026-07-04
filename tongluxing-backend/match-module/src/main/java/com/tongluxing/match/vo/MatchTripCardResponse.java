package com.tongluxing.match.vo;

/**
 * MatchTripCardResponse 响应数据对象。
 */
public record MatchTripCardResponse(
        String tripId,
        String userId,
        String startName,
        String endName,
        String departureTime,
        String travelDepth,
        Integer matchScore,
        Integer overlapRate,
        Integer departureGapMinutes,
        Integer distanceGapMeters
) {
}
