package com.tongdao.auth.vo;

/**
 * 刷新 access token 响应。
 */
public record RefreshTokenResponse(
        /** 新签发的 access token。 */
        String token,
        /** 新 access token 有效期，单位秒。 */
        Integer expireSeconds
) {
}
