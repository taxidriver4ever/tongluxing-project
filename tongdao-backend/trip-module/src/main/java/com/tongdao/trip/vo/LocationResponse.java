package com.tongdao.trip.vo;

import java.math.BigDecimal;

/**
 * LocationResponse 响应数据对象。
 */
public record LocationResponse(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
