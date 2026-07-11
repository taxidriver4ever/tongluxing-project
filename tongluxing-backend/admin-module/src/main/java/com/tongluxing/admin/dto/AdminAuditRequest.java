package com.tongluxing.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台审核请求。
 */
public record AdminAuditRequest(
        /** 审核结果，仅支持 APPROVED 或 REJECTED。 */
        @NotBlank String auditResult,
        /** 驳回原因；REJECTED 时由服务层强制校验。 */
        @Size(max = 255) String rejectReason,
        /** 请求幂等 ID，用于防止重复审核。 */
        @NotBlank String requestId
) {
}
