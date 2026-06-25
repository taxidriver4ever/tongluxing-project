package com.tongdao.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProfitSharingRequest(
        @NotNull Long orderId,
        @NotNull Long verificationId,
        @NotNull Long merchantId,
        @NotBlank String requestId
) {
}

