package com.tongdao.merchant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 奖励合作商家池配置实体，对应 merchant_reward_pool_config 表。
 */
@Data
public class MerchantRewardPoolConfig {
    private Long id;
    private Long merchantId;
    private Boolean enabled;
    private String couponType;
    private Integer monthlyStock;
    private Integer usedStock;
    private BigDecimal exposureWeightBonus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
