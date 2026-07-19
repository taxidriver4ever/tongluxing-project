package com.tongluxing.trip.vo;

/**
 * 行程结算结果。
 *
 * @param tripId 行程 ID
 * @param status 结算后的状态，固定为 SETTLED
 * @param memberCount 获得成长值的有效成员数
 * @param pointsPerMember 每位成员成长值
 * @param totalPoints 本次应发成长值总和
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
