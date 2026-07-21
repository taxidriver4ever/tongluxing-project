package com.tongluxing.auth.vo;

/**
 * 刷新 access token 响应。
 */
public record RefreshTokenResponse(
        /** 新签发的 access token。 */
        String token,
        /** 轮换后新签发的 refresh token；旧 refresh token 已立即失效。 */
        String refreshToken,
        /** 新 access token 有效期，单位秒。 */
        Integer expireSeconds
) {
}
