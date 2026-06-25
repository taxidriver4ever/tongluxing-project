package com.tongdao.payment.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefundCallbackRequest(
        @NotNull Long refundId,
        @NotBlank String wxRefundId,
        LocalDateTime refundedAt,
        String rawPayload
) {
}

