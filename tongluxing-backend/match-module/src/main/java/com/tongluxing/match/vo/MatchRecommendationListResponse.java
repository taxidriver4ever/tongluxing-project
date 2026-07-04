package com.tongluxing.match.vo;

import java.util.List;

/**
 * MatchRecommendationListResponse 响应数据对象。
 */
public record MatchRecommendationListResponse(
        String tripId,
        List<MatchTripCardResponse> trips,
        List<MatchTeamCardResponse> teams
) {
}
