package com.tongdao.verification.dto;

import jakarta.validation.constraints.NotBlank;
/**
 * ReversalApplyRequest 请求对象。
 */

public record ReversalApplyRequest(
        @NotBlank String reason,
        @NotBlank String requestId
) {
}
