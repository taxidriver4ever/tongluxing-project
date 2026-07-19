package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Web Admin 固定账号登录请求。 */
public record AdminLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String password
) {
}
