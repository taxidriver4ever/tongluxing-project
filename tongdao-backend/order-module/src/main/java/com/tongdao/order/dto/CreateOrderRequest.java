package com.tongdao.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
/**
 * CreateOrderRequest 请求对象。
 */

public record CreateOrderRequest(
        @NotNull Long productId,
        Long activityId,
        @NotNull @Min(1) Integer quantity,
        Long userCouponId,
        Boolean useBestCoupon,
        @Size(max = 255) String remark,
        @NotBlank @Size(max = 128) String requestId
) {
}

