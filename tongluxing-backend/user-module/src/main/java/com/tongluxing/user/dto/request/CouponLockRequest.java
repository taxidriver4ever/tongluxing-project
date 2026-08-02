package com.tongluxing.user.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 订单锁定优惠券请求。
 *
 * @param orderId 要锁定优惠券的订单 ID
 * @param amount 订单参与优惠计算的非负金额
 */
public record CouponLockRequest(@NotNull Long orderId, @NotNull @DecimalMin("0.00") BigDecimal amount) {
}

