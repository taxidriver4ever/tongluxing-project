package com.tongluxing.merchant.vo;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 平台合作券池中的商家免费券。 */
public record PartnerCouponPoolVO(
        @JsonSerialize(using = ToStringSerializer.class) Long couponPoolId,
        @JsonSerialize(using = ToStringSerializer.class) Long merchantId,
        String merchantName,
        String couponName, String couponType, BigDecimal thresholdAmount,
        BigDecimal discountAmount, Integer totalStock, Integer usedStock,
        Integer validDays, String settlementMode, String auditStatus, String poolStatus
) {
}
