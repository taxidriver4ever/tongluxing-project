package com.tongluxing.merchant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家拼团商品实体，对应 merchant_product 表。
 */
@Data
public class MerchantProduct {
    private Long id;
    private Long merchantId;
    private String productName;
    private String productType;
    private BigDecimal originalPrice;
    private BigDecimal groupPrice;
    private String ladderPriceJson;
    private Integer targetPeople;
    private Integer stock;
    private Integer validHours;
    private BigDecimal minSettlementPrice;
    private String imageKeysJson;
    private String description;
    private String productStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
