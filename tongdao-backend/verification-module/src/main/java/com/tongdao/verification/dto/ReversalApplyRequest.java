package com.tongdao.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record ReversalApplyRequest(
        @NotBlank String reason,
        @NotBlank String requestId
) {
}
