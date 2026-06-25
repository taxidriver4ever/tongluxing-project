package com.tongdao.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminConfigUpdateRequest(
        @NotBlank String configKey,
        @NotBlank String configValue,
        LocalDateTime effectiveAt,
        @NotNull Long operatorId,
        @NotBlank String requestId,
        String changeReason
) {
}
