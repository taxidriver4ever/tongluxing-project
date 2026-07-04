package com.tongluxing.merchant.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发布拼团商品请求。
 *
 * <p>merchant-module 只维护商品资料，真实拼团活动、支付和退款不在本模块处理。</p>
 */
public record CreateMerchantProductRequest(
        @NotBlank @Size(max = 128) String productName,
        @NotBlank @Size(max = 32) String productType,
        @NotNull @DecimalMin("0.01") BigDecimal originalPrice,
        @NotNull @DecimalMin("0.01") BigDecimal groupPrice,
        String ladderPriceJson,
        @NotNull @Min(1) Integer targetPeople,
        @NotNull @Min(0) Integer stock,
        @NotNull @Min(1) Integer validHours,
        @DecimalMin("0.00") BigDecimal minSettlementPrice,
        List<@Size(max = 512) String> imageKeys,
        @Size(max = 1000) String description
) {
}
