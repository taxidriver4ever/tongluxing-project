package com.tongdao.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderPreviewRequest(
        @NotNull Long productId,
        Long activityId,
        @NotNull @Min(1) Integer quantity,
        Long userCouponId,
        Boolean useBestCoupon
) {
}

