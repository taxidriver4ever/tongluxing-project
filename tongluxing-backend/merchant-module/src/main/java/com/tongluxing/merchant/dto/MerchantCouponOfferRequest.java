package com.tongluxing.merchant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MerchantCouponOfferRequest(
        @NotNull Long storeId,
        @NotBlank @Size(max=128) String couponName,
        @NotBlank @Size(max=512) String coverImageKey,
        @NotBlank @Size(max=1000) String description,
        @NotBlank @Size(max=32) String category,
        @NotNull @DecimalMin("0.01") BigDecimal originalPrice,
        @NotNull @DecimalMin("0.01") BigDecimal salePrice,
        @NotNull @Min(1) Integer stock,
        @NotNull @Min(1) Integer limitCount,
        boolean groupEnabled,
        @Min(2) Integer groupPeople,
        @Min(1) Integer groupTimeoutHours,
        @NotNull LocalDateTime publishTime,
        @NotNull LocalDateTime expireTime,
        @NotNull LocalDateTime useStartTime,
        @NotNull LocalDateTime useEndTime,
        boolean reservationRequired,
        boolean refundable,
        boolean holidayAvailable,
        boolean stackable,
        @NotBlank @Size(max=1000) String useInstructions
) {}
