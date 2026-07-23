package com.tongluxing.merchant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 普通商户提交合作商申请。 */
public record MerchantPartnerApplicationRequest(
        @NotBlank @Size(max = 500) String applicationReason,
        @NotBlank @Size(max = 255) String cooperationCategories,
        @NotNull @Min(1) @Max(1000000) Integer plannedMonthlyStock
) {
}
