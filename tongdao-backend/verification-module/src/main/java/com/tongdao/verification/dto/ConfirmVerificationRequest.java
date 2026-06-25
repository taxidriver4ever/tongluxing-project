package com.tongdao.verification.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
