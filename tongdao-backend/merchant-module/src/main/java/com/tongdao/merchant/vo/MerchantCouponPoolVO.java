package com.tongdao.merchant.vo;

import java.math.BigDecimal;

/**
 * 商家券池配置返回对象。
 */
public record MerchantCouponPoolVO(
        Long couponPoolId,
        Long merchantId,
        String couponName,
        String couponType,
        String sourceType,
        BigDecimal thresholdAmount,
        BigDecimal discountAmount,
        BigDecimal discountRate,
        Integer totalStock,
        Integer usedStock,
        Integer validDays,
        String settlementMode,
        String auditStatus,
        String poolStatus
) {
}
