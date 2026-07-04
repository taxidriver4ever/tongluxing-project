package com.tongluxing.verification.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * ConfirmVerificationRequest 请求对象。
 */

public record ConfirmVerificationRequest(
        @NotBlank String verificationCode,
        @NotNull Long merchantId,
        @NotNull Long operatorId,
        String locationName,
        BigDecimal longitude,
        BigDecimal latitude,
        @NotBlank String requestId
) {
}
