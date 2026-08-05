package com.tongluxing.match.vo;

import java.util.List;

/**
 * App「行程-推荐」分页响应。
 *
 * @param total 统一过滤后的结果总数
 * @param list 当前页推荐卡片
 * @param userHasTrip 服务端实际判断的“用户是否有可作为推荐基准的自有行程”
 * @param effectiveSort 实际排序：MATCH_RATE、HEAT、DISTANCE 或 TIME
 * @param referenceTripId 有自有行程时使用的基准行程 ID，无行程时为空
 */
public record TripRecommendPageResponse(
        Long total,
        List<TripRecommendCardResponse> list,
        Boolean userHasTrip,
        String effectiveSort,
        String referenceTripId
) {
}
