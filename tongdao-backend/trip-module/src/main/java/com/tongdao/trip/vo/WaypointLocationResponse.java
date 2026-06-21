package com.tongdao.trip.vo;

import java.math.BigDecimal;

public record WaypointLocationResponse(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer sortOrder
) {
}
