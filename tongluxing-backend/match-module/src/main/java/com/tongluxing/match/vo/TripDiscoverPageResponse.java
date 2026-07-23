package com.tongluxing.match.vo;

import java.util.List;

/** 稳定分页的公共发现行程响应。 */
public record TripDiscoverPageResponse(
        Integer page, Integer size, Long total, List<TripDiscoverCardResponse> records
) {
}
