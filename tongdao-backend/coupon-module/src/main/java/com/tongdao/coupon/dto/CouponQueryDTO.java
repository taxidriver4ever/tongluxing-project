package com.tongdao.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CouponQueryDTO {
    private Long id;
    private Long userId;
    private Long templateId;
    private String couponName;
    private String couponType;
    private Long issuerId;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal deductionAmount;
    private String scopeJson;
    private String couponStatus;
    private LocalDateTime validStartAt;
    private LocalDateTime validEndAt;
    private Long lockedOrderId;
    private Long usedOrderId;
    private String validityType;
    private Integer validDays;
}
