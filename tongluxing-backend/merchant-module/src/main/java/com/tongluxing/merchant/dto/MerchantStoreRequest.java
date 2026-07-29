package com.tongluxing.merchant.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 封装商家门店请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
public record MerchantStoreRequest(
        @NotBlank @Size(max=128) String storeName,
        @NotBlank @Size(max=255) String address,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal latitude,
        @NotBlank @Pattern(regexp="^1[3-9]\\d{9}$") String contactPhone,
        @NotBlank @Size(max=128) String businessHours,
        @Size(max=255) String parkingInfo
) {}
