package com.tongluxing.map.vo;

import java.math.BigDecimal;

/** 当前用户的地点搜索历史；historyId 仅用于删除该条历史。 */
public record LocationHistoryResponse(
        String historyId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer distanceMeters
) {
}
