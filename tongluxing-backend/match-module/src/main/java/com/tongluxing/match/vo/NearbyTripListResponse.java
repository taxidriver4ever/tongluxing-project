package com.tongluxing.match.vo;

import java.util.List;

/**
 * 以当前位置为圆心的公开招募行程列表。
 *
 * @param trips 按起点距离由近到远排列的行程卡片
 */
public record NearbyTripListResponse(
        List<MatchTripCardResponse> trips
) {
}
