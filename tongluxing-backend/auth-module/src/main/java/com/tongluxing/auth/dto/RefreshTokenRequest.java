package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 access token 请求。
 */
public record RefreshTokenRequest(
        /** 登录时返回的 refresh token。 */
        @NotBlank(message = "刷新令牌不能为空")
        String refreshToken
) {
}
