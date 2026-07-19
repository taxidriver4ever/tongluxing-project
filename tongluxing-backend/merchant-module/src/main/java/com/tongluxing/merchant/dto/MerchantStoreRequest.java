package com.tongluxing.merchant.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantStoreRequest(
        @NotBlank @Size(max=128) String storeName,
        @NotBlank @Size(max=255) String address,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal latitude,
        @NotBlank @Pattern(regexp="^1[3-9]\\d{9}$") String contactPhone,
        @NotBlank @Size(max=128) String businessHours,
        @Size(max=255) String parkingInfo
) {}
