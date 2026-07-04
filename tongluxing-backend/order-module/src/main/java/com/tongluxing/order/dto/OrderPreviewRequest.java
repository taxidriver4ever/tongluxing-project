package com.tongluxing.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
/**
 * OrderPreviewRequest 请求对象。
 */

public record OrderPreviewRequest(
        @NotNull Long productId,
        Long activityId,
        @NotNull @Min(1) Integer quantity,
        Long userCouponId,
        Boolean useBestCoupon
) {
}

