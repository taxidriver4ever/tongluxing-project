package com.tongluxing.match.vo;

import java.util.List;

/**
 * 一条源行程的持久化推荐列表。
 *
 * @param tripId 当前用户的源行程 ID
 * @param trips 按匹配分排序的目标行程卡片
 * @param teams 从可申请目标行程中派生的车队卡片
 */
public record MatchRecommendationListResponse(
        String tripId,
        List<MatchTripCardResponse> trips,
        List<MatchTeamCardResponse> teams
) {
}
