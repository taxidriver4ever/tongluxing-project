package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 运营后台退款读模型。 */
public record AdminRefundVO(
        Long refundId, String refundNo, Long orderId, String orderNo,
        Long userId, String userName, Long merchantId, String merchantName,
        BigDecimal refundAmount, String refundReason, String refundType,
        String refundStatus, String auditStatus, LocalDateTime requestedAt,
        LocalDateTime refundedAt) {}
