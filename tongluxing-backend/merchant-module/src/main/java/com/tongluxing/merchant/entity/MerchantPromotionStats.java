package com.tongluxing.merchant.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家推广聚合数据实体，对应 merchant_promotion_stats 表。
 */
@Data
public class MerchantPromotionStats {
    private Long id;
    private Long merchantId;
    private Long promotionCodeId;
    private LocalDate statDate;
    private Long registerCount;
    private Long couponClaimCount;
    private Long couponVerifyCount;
    private Long orderCount;
    private BigDecimal tradeAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
