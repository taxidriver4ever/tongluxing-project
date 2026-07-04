package com.tongluxing.merchant.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建商家券池配置请求。
 *
 * <p>这里配置的是商家愿意提供的券预算和库存，用户券实例仍由 coupon-module 管理。</p>
 */
public record CreateMerchantCouponPoolRequest(
        @NotBlank @Size(max = 128) String couponName,
        @NotBlank @Size(max = 32) String couponType,
        @NotBlank @Size(max = 32) String sourceType,
        @DecimalMin("0.00") BigDecimal discountAmount,
        @DecimalMin("0.0000") BigDecimal discountRate,
        @DecimalMin("0.00") BigDecimal thresholdAmount,
        @NotNull @Min(1) Integer totalStock,
        @NotNull @Min(1) Integer validDays,
        @NotBlank @Size(max = 32) String settlementMode
) {
}
