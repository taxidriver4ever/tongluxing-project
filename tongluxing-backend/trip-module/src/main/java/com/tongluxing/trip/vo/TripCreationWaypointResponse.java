package com.tongluxing.trip.vo;

import java.math.BigDecimal;

/** 创建行程草稿中的经停点。 */
public record TripCreationWaypointResponse(
        String waypointId, String name, String address, BigDecimal longitude, BigDecimal latitude,
        String type, Integer sort, Integer stayMinutes
) { }
