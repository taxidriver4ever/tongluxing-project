package com.tongluxing.auth.security;

/**
 * 当前请求的认证主体。
 *
 * <p>Token 校验通过后会把该对象放入 Spring Security 上下文，后续业务可从上下文读取当前用户。</p>
 *
 * <p>普通用户的 phone 是登录手机号；Admin 主体复用该轻量结构时，该字段保存后台用户名。
 * 权限必须读取 Authentication 的 authorities，不能根据 phone 或 deviceId 推断角色。</p>
 *
 * @param userId 普通用户 ID 或后台操作员 ID
 * @param phone 普通用户手机号；Admin 会话中为后台用户名
 * @param token 当前请求使用的原始 Bearer Token，退出时用于精确撤销
 * @param deviceId JWT 中的设备标识；Admin 固定为 admin-web
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
