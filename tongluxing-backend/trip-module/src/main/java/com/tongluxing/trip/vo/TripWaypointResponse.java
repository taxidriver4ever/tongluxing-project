package com.tongluxing.trip.vo;

import java.math.BigDecimal;

/**
 * TripWaypointResponse 响应数据对象。
 */
public record TripWaypointResponse(
        String waypointId,
        Integer seqNo,
        String placeName,
        BigDecimal lat,
        BigDecimal lng,
        Integer stayMinutes
) {
}
