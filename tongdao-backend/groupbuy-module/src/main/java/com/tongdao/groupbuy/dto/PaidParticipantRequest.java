package com.tongdao.groupbuy.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaidParticipantRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        LocalDateTime paidAt,
        @NotBlank String requestId
) {
}

