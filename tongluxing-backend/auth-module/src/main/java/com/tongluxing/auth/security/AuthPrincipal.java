package com.tongluxing.auth.security;

/**
 * 当前请求的认证主体。
 *
 * <p>Token 校验通过后会把该对象放入 Spring Security 上下文，后续业务可从上下文读取当前用户。</p>
 */
public record AuthPrincipal(
        /** 当前登录用户 ID。 */
        Long userId,
        /** 当前登录手机号。 */
        String phone,
        /** 原始 access token，退出登录时用于反查 jti。 */
        String token,
        /** 登录设备标识。 */
        String deviceId
) {
}
