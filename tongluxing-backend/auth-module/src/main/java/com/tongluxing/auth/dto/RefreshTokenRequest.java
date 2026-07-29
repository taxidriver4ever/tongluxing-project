package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 access token 请求。
 *
 * <p>刷新采用轮换策略：成功后旧 access token 与旧 refresh token 都会撤销，
 * 客户端必须原子替换响应中的两个新令牌。</p>
 *
 * @param refreshToken 登录或上一次刷新返回的刷新令牌，只能使用一次
 */
public record RefreshTokenRequest(
        /** 登录时返回的 refresh token。 */
        @NotBlank(message = "刷新令牌不能为空")
        String refreshToken
) {
}
