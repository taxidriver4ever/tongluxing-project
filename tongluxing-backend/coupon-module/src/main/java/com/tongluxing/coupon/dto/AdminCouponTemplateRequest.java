package com.tongluxing.coupon.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin 创建平台券或录入平台采购券。 */
public record AdminCouponTemplateRequest(
        @NotBlank @Size(max=64) String couponName,
        @NotBlank @Size(max=24) String couponType,
        Long issuerId,
        @NotNull @DecimalMin("0.00") BigDecimal thresholdAmount,
        @NotNull @DecimalMin("0.01") BigDecimal discountAmount,
        @NotNull @Min(1) @Max(10000000) Integer totalQuantity,
        @NotNull @Min(1) @Max(3650) Integer validDays,
        @NotNull @Min(1) @Max(100) Integer perUserLimit,
        @NotBlank @Size(max=2000) String scopeJson
) {
}
