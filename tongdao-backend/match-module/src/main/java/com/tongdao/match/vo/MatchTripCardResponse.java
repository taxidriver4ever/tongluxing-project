package com.tongdao.match.vo;

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
