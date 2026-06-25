package com.tongdao.groupbuy.vo;

import java.time.LocalDateTime;

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

