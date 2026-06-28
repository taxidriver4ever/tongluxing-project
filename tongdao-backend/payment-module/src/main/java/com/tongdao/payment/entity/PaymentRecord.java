package com.tongdao.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * PaymentRecord 数据库实体。
 */

@Data
public class PaymentRecord {
    private Long id;
    private Long orderId;
    private String orderNo;
    private String paymentNo;
    private String wxPrepayId;
    private String wxTransactionId;
    private String payChannel;
    private BigDecimal payAmount;
    private String paymentStatus;
    private String callbackPayload;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

