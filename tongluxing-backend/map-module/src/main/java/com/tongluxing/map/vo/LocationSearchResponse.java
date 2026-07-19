package com.tongluxing.map.vo;

import java.math.BigDecimal;

/** 地点搜索建议，包含相对当前位置的直线距离。 */
public record LocationSearchResponse(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer distanceMeters
) {
}
