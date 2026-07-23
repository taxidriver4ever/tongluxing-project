package com.tongluxing.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 合作商申请取消平台合作。 */
public record MerchantPartnerCancellationRequest(
        @NotBlank(message = "请填写取消合作原因")
        @Size(min = 2, max = 500, message = "取消合作原因长度需为2-500字")
        String reason
) {
}
