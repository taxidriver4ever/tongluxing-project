package com.tongluxing.match.vo;

import java.util.List;

/** 分页行程搜索结果。 */
public record TripSearchPageResponse(
        Integer page,
        Integer size,
        Long total,
        List<TripSearchCardResponse> records
) {
}
