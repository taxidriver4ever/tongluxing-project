package com.tongluxing.user.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 当前订单可用优惠券返回对象。
 *
 * @param id 用户优惠券主键
 * @param couponName 展示名称
 * @param deductionAmount 针对当前订单实际可抵扣的金额，而非模板固定面额
 * @param validEndAt 失效时间
 */
public record AvailableCouponVO(Long id, String couponName, BigDecimal deductionAmount, LocalDateTime validEndAt) {
}

