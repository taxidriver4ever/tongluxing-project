package com.tongluxing.groupbuy.vo;

import java.time.LocalDateTime;

/**
 * 拼团参与人响应视图。
 *
 * @param participantId 参与记录 ID
 * @param activityId 所属拼团活动 ID
 * @param orderId 关联订单 ID
 * @param userId 参团用户 ID
 * @param participantStatus 参与状态，如 PAID、REFUNDED
 * @param joinedAt 加入拼团时间
 * @param paidAt 支付完成时间
 * @param refundedAt 退款完成时间
 */
public record GroupbuyParticipantVO(
        Long participantId,
        Long activityId,
        Long orderId,
        Long userId,
        String participantStatus,
        LocalDateTime joinedAt,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
}

