package com.tongluxing.assessment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家考核结果。
 */
public record MerchantAssessmentResultVO(
        Long merchantId,
        String period,
        BigDecimal score,
        String level,
        BigDecimal commissionRate,
        BigDecimal rankWeight,
        BigDecimal exclusionRadiusKm,
        String nextLevel,
        BigDecimal needScore,
        LocalDateTime calculatedAt) {
}
