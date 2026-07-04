package com.tongluxing.groupbuy.vo;

import java.time.LocalDateTime;
/**
 * GroupbuyParticipantVO 视图响应对象。
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

