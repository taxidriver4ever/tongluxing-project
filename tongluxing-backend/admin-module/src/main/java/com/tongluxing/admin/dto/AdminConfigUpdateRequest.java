package com.tongluxing.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台运营配置更新请求。
 */
public record AdminConfigUpdateRequest(
        /** 配置键。 */
        @NotBlank @Size(max = 128) String configKey,
        /** 配置值，通常为 JSON 字符串。 */
        @NotBlank @Size(max = 8000) String configValue,
        /** 生效时间；为空时立即生效。 */
        LocalDateTime effectiveAt,
        /** 兼容旧前端；后端审计身份只使用 Admin Token。 */
        Long operatorId,
        /** 请求幂等 ID。 */
        @NotBlank @Size(max = 128) String requestId,
        /** 配置变更原因。 */
        @Size(max = 200) String changeReason
) {
}
