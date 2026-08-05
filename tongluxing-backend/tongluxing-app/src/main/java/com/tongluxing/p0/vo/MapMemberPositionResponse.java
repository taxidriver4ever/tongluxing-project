package com.tongluxing.p0.vo;

import java.math.BigDecimal;

/** 首页行程地图中的成员实时位置。 */
public record MapMemberPositionResponse(
        String userId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speed,
        BigDecimal direction,
        String recordTime,
        Boolean captain,
        Boolean stale
) {
}
