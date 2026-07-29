package com.tongluxing.auth.vo;

/**
 * 刷新 access token 响应。
 *
 * <p>服务端执行 refresh token 轮换，响应生成后请求中的旧 refresh token 已不可再用。</p>
 *
 * @param token 新 access token
 * @param refreshToken 与新 access token 配对的新 refresh token
 * @param expireSeconds 新 access token 有效期，单位秒
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
