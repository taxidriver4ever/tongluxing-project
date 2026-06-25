package com.tongdao.admin.vo;

import java.math.BigDecimal;

public record AdminOperationOverviewVO(
        Long registeredUserCount,
        Long certifiedVehicleCount,
        Long merchantCount,
        Long groupbuyActivityCount,
        Long orderCount,
        BigDecimal paidAmount,
        Long verificationCount,
        BigDecimal commissionAmount,
        Long inviteCount,
        Long couponIssuedCount,
        Long couponVerifiedCount
) {
}
