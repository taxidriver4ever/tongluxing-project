package com.tongluxing.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家只读结算记录。 */
public record MerchantSettlementVO(Long settlementId, Long orderId, String orderNo,
                                   BigDecimal totalAmount, BigDecimal commissionAmount,
                                   BigDecimal merchantAmount, BigDecimal commissionRate,
                                   String settlementStatus, LocalDateTime createdAt,
                                   LocalDateTime settledAt) {}
