package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.util.List;

/** 运营总览实时统计与近 7 日趋势。 */
public record AdminOperationOverviewVO(
        Long registeredUserCount, Long activeUserCount, Long newUserCount,
        Long certifiedVehicleCount, Long merchantCount, Long pendingMerchantCount,
        Long activeMerchantCount, Long groupbuyActivityCount, Long orderCount,
        Long todayOrderCount, BigDecimal paidAmount, BigDecimal todayPaidAmount,
        Long verificationCount, BigDecimal commissionAmount, Long couponOfferCount,
        Long pendingRefundCount, Long pendingSettlementCount,
        List<AdminTrendPointVO> trends) {}
