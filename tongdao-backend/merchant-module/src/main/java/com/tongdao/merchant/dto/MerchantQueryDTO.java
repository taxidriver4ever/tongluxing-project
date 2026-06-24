package com.tongdao.merchant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家模块查询结果 DTO。
 *
 * <p>用于承接商家资料、商品、券池、奖励池、推广码和统计表的查询结果，
 * 服务层再转换为对应 VO。</p>
 */
@Data
public class MerchantQueryDTO {
    private Long id;
    private Long merchantId;
    private Long userId;
    private String merchantName;
    private String category;
    private String contactName;
    private String contactPhoneMask;
    private String provinceCode;
    private String cityCode;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coverImageKey;
    private String description;
    private String auditStatus;
    private String merchantLevel;
    private BigDecimal score;
    private BigDecimal commissionRate;
    private BigDecimal rankWeight;
    private BigDecimal exclusionRadiusKm;
    private String status;
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
    private String productStatus;
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
    private String poolStatus;
    private Boolean enabled;
    private Integer monthlyStock;
    private BigDecimal exposureWeightBonus;
    private String promotionCode;
    private String channelName;
    private String scene;
    private String qrImageKey;
    private String remark;
    private LocalDate statDate;
    private Long exposureCount;
    private Long clickCount;
    private Long registerCount;
    private Long couponClaimCount;
    private Long couponVerifyCount;
    private Long orderCount;
    private BigDecimal tradeAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
