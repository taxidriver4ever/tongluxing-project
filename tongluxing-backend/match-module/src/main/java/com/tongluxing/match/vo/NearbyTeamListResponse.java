package com.tongluxing.match.vo;

import java.util.List;

/**
 * NearbyTeamListResponse 响应数据对象。
 */
public record NearbyTeamListResponse(
        List<MatchTeamCardResponse> teams
) {
}
