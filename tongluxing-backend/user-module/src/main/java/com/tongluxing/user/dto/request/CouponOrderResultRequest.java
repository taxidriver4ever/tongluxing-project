package com.tongluxing.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 订单支付结果通知请求。
 *
 * @param orderId 已锁券订单 ID
 * @param payStatus SUCCESS、FAILED 或 CANCELLED
 */
public record CouponOrderResultRequest(
        @NotNull Long orderId,
        @NotBlank @Pattern(regexp = "SUCCESS|FAILED|CANCELLED") String payStatus
) {
}

