package com.tongluxing.auth.vo;

/**
 * Web Admin 登录成功响应。
 *
 * <p>Admin 使用随机不透明 Token，而不是普通用户 JWT。客户端仍通过
 * {@code Authorization: Bearer <token>} 发送，服务端只在 Redis 中保存其 SHA-256 摘要。</p>
 *
 * @param token 256 位安全随机会话令牌，仅在本次登录响应中返回明文
 * @param operatorId 后台操作员业务 ID
 * @param username 后台登录名
 * @param displayName 后台展示名称
 * @param expireSeconds 会话剩余有效期，单位秒
 */
public record AdminLoginResponse(
        String token,
        Long operatorId,
        String username,
        String displayName,
        Integer expireSeconds
) {
}
