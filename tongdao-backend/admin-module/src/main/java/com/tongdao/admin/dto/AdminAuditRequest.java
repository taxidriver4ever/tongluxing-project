package com.tongdao.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 后台审核请求。
 */
public record AdminAuditRequest(
        /** 审核结果，仅支持 APPROVED 或 REJECTED。 */
        @NotBlank String auditResult,
        /** 拒绝原因；审核拒绝时建议填写。 */
        String rejectReason,
        /** 操作人 ID。 */
        @NotNull Long operatorId,
        /** 请求幂等 ID，用于防止重复审核。 */
        @NotBlank String requestId
) {
}
