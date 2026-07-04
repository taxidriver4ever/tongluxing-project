package com.tongluxing.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * OrderTrade 数据库实体。
 */

@Data
public class OrderTrade {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long productId;
    private Long activityId;
    private BigDecimal originalAmount;
    private BigDecimal groupbuyDiscountAmount;
    private BigDecimal couponDeductionAmount;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private Long userCouponId;
    private String orderStatus;
    private String paymentStatus;
    private String verificationStatus;
    private String refundStatus;
    private String profitSharingStatus;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

