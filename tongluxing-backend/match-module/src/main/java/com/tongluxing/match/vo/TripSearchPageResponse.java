package com.tongluxing.match.vo;

import java.util.List;

/**
 * 高级条件搜索的分页结果。
 *
 * @param page 当前页码
 * @param size 每页数量
 * @param total Java 候选过滤完成后的总数
 * @param records 当前页公开卡片
 */
public record TripSearchPageResponse(
        Integer page,
        Integer size,
        Long total,
        List<TripSearchCardResponse> records
) {
}
