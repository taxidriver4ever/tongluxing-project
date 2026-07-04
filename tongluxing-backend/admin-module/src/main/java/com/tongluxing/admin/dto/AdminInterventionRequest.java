package com.tongluxing.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 后台人工干预请求。
 */
public record AdminInterventionRequest(
        /** 干预动作，例如 FORCE_SUCCESS、FORCE_FAILED、OFFLINE。 */
        @NotBlank String action,
        /** 干预原因。 */
        @NotBlank String reason,
        /** 操作人 ID。 */
        @NotNull Long operatorId,
        /** 请求幂等 ID。 */
        @NotBlank String requestId
) {
}
