package com.tongdao.trip.vo;

import java.math.BigDecimal;

public record TripWaypointResponse(
        String waypointId,
        Integer seqNo,
        String placeName,
        BigDecimal lat,
        BigDecimal lng,
        Integer stayMinutes
) {
}
