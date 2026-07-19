package com.tongluxing.auth.vo;

/** Web Admin 登录成功响应。 */
public record AdminLoginResponse(
        String token,
        Long operatorId,
        String username,
        String displayName,
        Integer expireSeconds
) {
}
