package com.tongluxing.trip.vo;

/**
 * 行程结算结果。
 *
 * @param tripId 行程 ID
 * @param status 结算后的状态，固定为 SETTLED
 * @param memberCount 本次结算的有效成员数
 * @param pointsPerMember 行程结束固定奖励（现为 0，成长值按累计每 50 公里实时发放）
 * @param totalPoints 本次行程结束固定奖励总和
 * @param duplicate 是否为重复结算请求
 * @param settledAt 结算完成时间
 */
public record TripSettlementResponse(
        String tripId,
        String status,
        Integer memberCount,
        Integer pointsPerMember,
        Integer totalPoints,
        Boolean duplicate,
        String settledAt
) {
}
