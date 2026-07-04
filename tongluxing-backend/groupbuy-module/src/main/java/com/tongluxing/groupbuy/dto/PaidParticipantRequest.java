package com.tongluxing.groupbuy.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * PaidParticipantRequest 请求对象。
 */

public record PaidParticipantRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        LocalDateTime paidAt,
        @NotBlank String requestId
) {
}

