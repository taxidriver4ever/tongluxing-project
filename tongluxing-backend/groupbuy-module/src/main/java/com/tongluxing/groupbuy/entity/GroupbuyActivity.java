package com.tongluxing.groupbuy.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * GroupbuyActivity 数据库实体。
 */

@Data
public class GroupbuyActivity {
    private Long id;
    private Long merchantId;
    private Long productId;
    private Long initiatorUserId;
    private Integer targetPeople;
    private Integer currentPeople;
    private BigDecimal groupPrice;
    private String ladderPriceJson;
    private String activityStatus;
    private LocalDateTime startAt;
    private LocalDateTime expireAt;
    private LocalDateTime successAt;
    private LocalDateTime failedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

