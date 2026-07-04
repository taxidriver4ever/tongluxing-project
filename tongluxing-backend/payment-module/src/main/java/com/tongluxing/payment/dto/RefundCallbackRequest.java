package com.tongluxing.payment.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * RefundCallbackRequest 请求对象。
 */

public record RefundCallbackRequest(
        @NotNull Long refundId,
        @NotBlank String wxRefundId,
        LocalDateTime refundedAt,
        String rawPayload
) {
}

