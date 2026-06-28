package com.tongdao.match.vo;

/**
 * MatchTeamCardResponse 响应数据对象。
 */
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
