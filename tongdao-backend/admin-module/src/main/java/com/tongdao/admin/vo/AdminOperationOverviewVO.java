package com.tongdao.admin.vo;

import java.math.BigDecimal;

/**
 * 运营概览响应。
 */
public record AdminOperationOverviewVO(
        /** 注册用户数。 */
        Long registeredUserCount,
        /** 已认证车辆数。 */
        Long certifiedVehicleCount,
        /** 商家数。 */
        Long merchantCount,
        /** 拼团活动数。 */
        Long groupbuyActivityCount,
        /** 订单数。 */
        Long orderCount,
        /** 支付金额汇总。 */
        BigDecimal paidAmount,
        /** 核销次数。 */
        Long verificationCount,
        /** 佣金金额汇总。 */
        BigDecimal commissionAmount,
        /** 邀请关系数。 */
        Long inviteCount,
        /** 发券数量。 */
        Long couponIssuedCount,
        /** 已核销券数量。 */
        Long couponVerifiedCount
) {
}
