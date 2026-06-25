package com.tongdao.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminInterventionRequest(
        @NotBlank String action,
        @NotBlank String reason,
        @NotNull Long operatorId,
        @NotBlank String requestId
) {
}
