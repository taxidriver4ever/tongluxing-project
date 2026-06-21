package com.tongdao.match.vo;

import java.util.List;

public record NearbyTeamListResponse(
        List<MatchTeamCardResponse> teams
) {
}
