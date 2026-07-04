package com.tongluxing.trip.vo;

import java.util.List;

/**
 * TripListResponse 响应数据对象。
 */
public record TripListResponse(
        List<TripResponse> trips
) {
}
