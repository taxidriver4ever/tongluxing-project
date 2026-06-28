package com.tongdao.order.vo;

import java.math.BigDecimal;
/**
 * OrderPreviewVO 视图响应对象。
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

