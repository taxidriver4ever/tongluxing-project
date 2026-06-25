package com.tongdao.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminAuditRequest(
        @NotBlank String auditResult,
        String rejectReason,
        @NotNull Long operatorId,
        @NotBlank String requestId
) {
}
