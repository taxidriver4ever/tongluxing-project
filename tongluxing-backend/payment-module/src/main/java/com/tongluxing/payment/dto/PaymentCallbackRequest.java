package com.tongluxing.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * PaymentCallbackRequest 请求对象。
 */

public record PaymentCallbackRequest(
        @NotNull Long orderId,
        @NotBlank String transactionId,
        @NotNull BigDecimal payAmount,
        LocalDateTime paidAt,
        String rawPayload
) {
}

