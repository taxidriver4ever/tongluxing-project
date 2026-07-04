package com.tongluxing.merchant.vo;

import java.math.BigDecimal;

/**
 * 商家考核中心返回对象。
 */
public record MerchantAssessmentVO(
        String merchantLevel,
        BigDecimal score,
        BigDecimal commissionRate,
        BigDecimal rankWeight,
        BigDecimal exclusionRadiusKm,
        String nextLevel,
        BigDecimal nextLevelNeedScore
) {
}
