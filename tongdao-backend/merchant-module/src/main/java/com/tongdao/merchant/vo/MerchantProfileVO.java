package com.tongdao.merchant.vo;

import java.math.BigDecimal;

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
        BigDecimal exclusionRadiusKm
) {
}
