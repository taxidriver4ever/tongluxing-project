package com.tongluxing.trip.vo;

import java.util.List;

/**
 * App「我的行程」首页聚合数据。
 *
 * <p>P0 要求把“我发布的当前行程”和“我加入的当前行程”固定置顶，
 * 两者与普通推荐完全分离，避免用户在推荐流中寻找自己的行程。</p>
 */
public record MyTripDashboardResponse(
        TripResponse currentTrip,
        List<TripResponse> upcomingTrips,
        List<TripResponse> recentTrips,
        Integer activeCount,
        Integer historyCount,
        TripResponse publishedCurrentTrip,
        TripResponse joinedCurrentTrip
) {
}
