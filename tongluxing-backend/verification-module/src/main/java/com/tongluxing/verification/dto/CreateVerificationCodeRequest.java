package com.tongluxing.verification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * CreateVerificationCodeRequest 请求对象。
 */

public record CreateVerificationCodeRequest(
        @NotBlank String bizType,
        @NotNull Long bizId,
        @NotNull Long userId,
        @NotNull Long merchantId,
        Long orderId,
        Long userCouponId,
        @DecimalMin(value = "0.00") BigDecimal amount,
        @NotNull @Future LocalDateTime expireAt,
        @NotBlank String requestId
) {
}
