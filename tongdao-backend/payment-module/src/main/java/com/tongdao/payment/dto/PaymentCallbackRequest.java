package com.tongdao.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(
        @NotNull Long orderId,
        @NotBlank String transactionId,
        @NotNull BigDecimal payAmount,
        LocalDateTime paidAt,
        String rawPayload
) {
}

