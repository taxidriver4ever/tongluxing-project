package com.tongluxing.merchant.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 商家资料返回对象。
 */
public record MerchantProfileVO(
        Long merchantId,
        String merchantName,
        String category,
        String auditStatus,
        String merchantLevel,
        BigDecimal commissionRate,
        BigDecimal rankWeight,
        BigDecimal exclusionRadiusKm,
        String rejectReason,
        Map<String, Object> applicationDetails,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
}
