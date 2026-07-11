package com.tongluxing.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 下单前金额试算请求。
 *
 * @param productId 商品 ID
 * @param activityId 拼团活动 ID；传入后会按拼团价计算优惠
 * @param quantity 购买数量，必须大于 0
 * @param userCouponId 指定试算的用户优惠券 ID
 * @param useBestCoupon 是否由服务端自动选择最优券
 */
public record OrderPreviewRequest(
        @NotNull Long productId,
        Long activityId,
        @NotNull @Min(1) Integer quantity,
        Long userCouponId,
        Boolean useBestCoupon
) {
}

