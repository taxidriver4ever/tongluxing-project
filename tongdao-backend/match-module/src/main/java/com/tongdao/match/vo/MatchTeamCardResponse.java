package com.tongdao.match.vo;

public record MatchTeamCardResponse(
        String teamId,
        String tripId,
        String teamName,
        String startName,
        String endName,
        String departureTime,
        Integer currentMemberCount,
        Integer maxMemberCount,
        Integer matchScore,
        Integer overlapRate
) {
}
