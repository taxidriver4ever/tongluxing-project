package com.tongluxing.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 平台券模板及发放、领取、使用统计。 */
public record AdminCouponTemplateVO(
        @JsonSerialize(using = ToStringSerializer.class) Long templateId,
        String couponName, String couponType,
        @JsonSerialize(using = ToStringSerializer.class) Long issuerId,
        BigDecimal thresholdAmount, BigDecimal discountAmount, String scopeJson,
        Integer validDays, Integer totalQuantity, Integer claimedQuantity, Integer perUserLimit,
        String templateStatus, Long issuedCount, Long claimedCount, Long usedCount, Long verifiedCount,
        LocalDateTime createdAt
) {
}
