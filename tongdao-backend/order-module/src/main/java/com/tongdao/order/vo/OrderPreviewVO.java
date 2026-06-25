package com.tongdao.order.vo;

import java.math.BigDecimal;

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

