package com.tongluxing.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

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
        @Min(1) @Max(1440) Integer extendMinutes,
        /** 请求幂等 ID。 */
        @NotBlank String requestId
) {
}
