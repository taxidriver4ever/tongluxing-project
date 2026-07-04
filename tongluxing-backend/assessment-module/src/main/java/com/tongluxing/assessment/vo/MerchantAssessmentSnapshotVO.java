package com.tongluxing.assessment.vo;

import java.math.BigDecimal;

/**
 * 商家等级快照，供支付分账和推荐排序读取。
 */
public record MerchantAssessmentSnapshotVO(
        Long merchantId,
        String level,
        BigDecimal score,
        BigDecimal commissionRate,
        BigDecimal rankWeight,
        BigDecimal exclusionRadiusKm) {
}
