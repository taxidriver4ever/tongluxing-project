package com.tongdao.match.vo;

import java.util.List;

public record MatchRecommendationListResponse(
        String tripId,
        List<MatchTripCardResponse> trips,
        List<MatchTeamCardResponse> teams
) {
}
