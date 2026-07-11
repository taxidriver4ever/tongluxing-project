package com.tongluxing.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建订单请求。
 *
 * @param productId 商品 ID
 * @param activityId 拼团活动 ID；普通购买时为空
 * @param quantity 购买数量，必须大于 0
 * @param userCouponId 指定使用的用户优惠券 ID；不使用券时为空
 * @param useBestCoupon 是否由服务端自动选择最优券
 * @param remark 用户下单备注，最多 255 个字符
 * @param requestId 客户端生成的幂等请求号，用于防重复提交
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

