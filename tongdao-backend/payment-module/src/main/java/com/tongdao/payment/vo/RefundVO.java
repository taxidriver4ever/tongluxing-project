package com.tongdao.payment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundVO(
        Long refundId,
        Long orderId,
        String refundNo,
        BigDecimal refundAmount,
        String refundStatus,
        String auditStatus,
        LocalDateTime requestedAt,
        LocalDateTime refundedAt
) {
}

