package com.tongluxing.match.vo;

import java.util.List;

/**
 * 稳定分页的公共发现行程响应。
 *
 * @param page 从 1 开始的当前页码
 * @param size 实际采用的页大小，最大 30
 * @param total 所有过滤条件应用后的总记录数
 * @param records 当前页卡片
 */
public record TripDiscoverPageResponse(
        Integer page, Integer size, Long total, List<TripDiscoverCardResponse> records
) {
}
