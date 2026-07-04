package com.tongluxing.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * PaymentProfitSharingRecord 数据库实体。
 */

@Data
public class PaymentProfitSharingRecord {
    private Long id;
    private Long orderId;
    private Long merchantId;
    private Long verificationId;
    private String sharingNo;
    private String wxSharingId;
    private BigDecimal totalAmount;
    private BigDecimal platformCommissionAmount;
    private BigDecimal merchantAmount;
    private BigDecimal commissionRate;
    private String sharingStatus;
    private String callbackPayload;
    private LocalDateTime sharedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

