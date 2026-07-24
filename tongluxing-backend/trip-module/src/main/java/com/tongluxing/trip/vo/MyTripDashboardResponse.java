package com.tongluxing.trip.vo;

import java.util.List;

/**
 * App「我的行程」首页聚合数据。
 *
 * <p>只聚合当前用户自己的行程，不包含公开推荐数据；用于减少移动端首页的多次请求。
 */
public record MyTripDashboardResponse(
        TripResponse currentTrip,
        List<TripResponse> upcomingTrips,
        List<TripResponse> recentTrips,
        Integer activeCount,
        Integer historyCount
) {
}
