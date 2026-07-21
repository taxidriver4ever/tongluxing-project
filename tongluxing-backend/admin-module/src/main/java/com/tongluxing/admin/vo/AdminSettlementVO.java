package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 分账记录作为 V1 结算读模型。 */
public record AdminSettlementVO(
        Long settlementId, String settlementNo, Long orderId, String orderNo,
        Long merchantId, String merchantName, BigDecimal totalAmount,
        BigDecimal commissionAmount, BigDecimal merchantAmount,
        BigDecimal commissionRate, String settlementStatus,
        LocalDateTime createdAt, LocalDateTime settledAt) {}
