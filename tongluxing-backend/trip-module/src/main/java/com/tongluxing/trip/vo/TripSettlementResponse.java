package com.tongluxing.trip.vo;

/**
 * 行程结算结果。
 *
 * @param tripId 行程 ID
 * @param status 结算状态：SETTLED 或 REVIEW_REQUIRED
 * @param memberCount 本次结算的有效成员数
 * @param pointsPerMember 每位有效成员的成长值（单次行程每满 5 公里 10 点）
 * @param totalPoints 本次行程成长值总和
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
