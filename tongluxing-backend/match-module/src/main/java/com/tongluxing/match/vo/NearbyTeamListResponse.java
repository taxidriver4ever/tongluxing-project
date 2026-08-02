package com.tongluxing.match.vo;

import java.util.List;

/**
 * 附近公开活跃车队列表。
 *
 * @param teams 经过数量限制的车队卡片集合
 */
public record NearbyTeamListResponse(
        List<MatchTeamCardResponse> teams
) {
}
