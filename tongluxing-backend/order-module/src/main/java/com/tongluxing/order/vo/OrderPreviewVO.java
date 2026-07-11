package com.tongluxing.order.vo;

import java.math.BigDecimal;

/**
 * 下单前金额试算响应视图。
 *
 * @param productId 商品 ID
 * @param activityId 拼团活动 ID
 * @param originalAmount 商品原始总额
 * @param groupbuyDiscountAmount 拼团优惠金额
 * @param couponDeductionAmount 优惠券抵扣金额
 * @param payableAmount 应付金额
 * @param selectedCouponId 本次试算选中的用户优惠券 ID
 * @param priceDescription 价格计算说明
 */
public record OrderPreviewVO(
        Long productId,
        Long activityId,
        BigDecimal originalAmount,
        BigDecimal groupbuyDiscountAmount,
        BigDecimal couponDeductionAmount,
        BigDecimal payableAmount,
        Long selectedCouponId,
        String priceDescription
) {
}

