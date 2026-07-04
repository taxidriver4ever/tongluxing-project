package com.tongluxing.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 后台运营配置更新请求。
 */
public record AdminConfigUpdateRequest(
        /** 配置键。 */
        @NotBlank String configKey,
        /** 配置值，通常为 JSON 字符串。 */
        @NotBlank String configValue,
        /** 生效时间；为空时立即生效。 */
        LocalDateTime effectiveAt,
        /** 操作人 ID。 */
        @NotNull Long operatorId,
        /** 请求幂等 ID。 */
        @NotBlank String requestId,
        /** 配置变更原因。 */
        String changeReason
) {
}
