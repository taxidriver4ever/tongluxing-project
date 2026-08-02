package com.tongluxing.user.vo;

import java.math.BigDecimal;

/**
 * 优惠券抵扣结果返回对象。
 *
 * <p>用于在锁券或支付回调后返回本次订单的实际抵扣与最新状态。</p>
 *
 * @param userCouponId 用户优惠券主键
 * @param orderId 关联订单 ID
 * @param deductionAmount 实际抵扣金额
 * @param status 锁定或核销状态
 */
public record CouponDeductionVO(Long userCouponId, Long orderId, BigDecimal deductionAmount, String status) {
}

