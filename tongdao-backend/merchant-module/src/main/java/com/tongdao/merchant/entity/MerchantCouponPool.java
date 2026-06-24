package com.tongdao.merchant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家券池配置实体，对应 merchant_coupon_pool 表。
 */
@Data
public class MerchantCouponPool {
    private Long id;
    private Long merchantId;
    private String couponName;
    private String couponType;
    private String sourceType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private Integer totalStock;
    private Integer usedStock;
    private Integer validDays;
    private String settlementMode;
    private String auditStatus;
    private String poolStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
