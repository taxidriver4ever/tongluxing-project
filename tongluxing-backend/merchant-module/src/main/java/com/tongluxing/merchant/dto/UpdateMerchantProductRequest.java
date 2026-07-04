package com.tongluxing.merchant.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 编辑拼团商品请求。
 *
 * <p>字段均为可选，未传入表示保留原商品配置。</p>
 */
public record UpdateMerchantProductRequest(
        @Size(max = 128) String productName,
        @Size(max = 32) String productType,
        @DecimalMin("0.01") BigDecimal originalPrice,
        @DecimalMin("0.01") BigDecimal groupPrice,
        String ladderPriceJson,
        @Min(1) Integer targetPeople,
        @Min(0) Integer stock,
        @Min(1) Integer validHours,
        @DecimalMin("0.00") BigDecimal minSettlementPrice,
        List<@Size(max = 512) String> imageKeys,
        @Size(max = 1000) String description
) {
}
