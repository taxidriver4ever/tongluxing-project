package com.tongdao.trip.vo;

import java.math.BigDecimal;

/**
 * WaypointLocationResponse 响应数据对象。
 */
public record WaypointLocationResponse(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer sortOrder
) {
}
