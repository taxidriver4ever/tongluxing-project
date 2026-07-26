package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.util.List;

/** 运营总览实时统计、核心链路待办与近 7 日趋势。 */
public record AdminOperationOverviewVO(
        Long registeredUserCount, Long activeUserCount, Long newUserCount,
        Long certifiedDriverCount, Long certifiedVehicleCount,
        Long pendingDrivingLicenseCount, Long pendingVehicleCertificationCount,
        Long merchantCount, Long pendingMerchantCount, Long activeMerchantCount,
        Long pendingMerchantCouponCount, Long groupbuyActivityCount,
        Long orderCount, Long todayOrderCount, BigDecimal paidAmount, BigDecimal todayPaidAmount,
        Long verificationCount, BigDecimal commissionAmount, Long couponOfferCount,
        Long todayCouponIssuedCount, Long todayCouponUsedCount,
        Long todayPublishedTripCount, Long recruitingTripCount, Long runningTripCount,
        Long todayCompletedTripCount, Long openCustomerTicketCount,
        Long pendingChatReportCount, Long activeSosCount,
        Long pendingRefundCount, Long pendingSettlementCount,
        List<AdminTrendPointVO> trends) {
}
