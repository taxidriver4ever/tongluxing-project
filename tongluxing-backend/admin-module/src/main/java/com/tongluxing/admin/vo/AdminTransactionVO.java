package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付与退款统一交易流水。 */
public record AdminTransactionVO(
        String transactionType, Long transactionId, String transactionNo,
        Long orderId, String orderNo, BigDecimal amount, String status,
        String channel, LocalDateTime occurredAt) {}
