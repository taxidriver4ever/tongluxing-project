package com.tongluxing.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * ProfitSharingRequest 请求对象。
 */

public record ProfitSharingRequest(
        @NotNull Long orderId,
        @NotNull Long verificationId,
        @NotNull Long merchantId,
        @NotBlank String requestId
) {
}

