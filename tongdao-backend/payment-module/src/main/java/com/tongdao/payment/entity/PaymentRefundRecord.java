package com.tongdao.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * PaymentRefundRecord 数据库实体。
 */

@Data
public class PaymentRefundRecord {
    private Long id;
    private Long orderId;
    private String refundNo;
    private String wxRefundId;
    private Long userId;
    private BigDecimal refundAmount;
    private String refundReason;
    private String refundType;
    private String refundStatus;
    private String auditStatus;
    private String callbackPayload;
    private LocalDateTime requestedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

