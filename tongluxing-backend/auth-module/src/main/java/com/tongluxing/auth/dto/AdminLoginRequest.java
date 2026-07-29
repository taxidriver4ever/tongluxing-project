package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Web Admin 固定账号登录请求。
 *
 * <p>当前后台处于联调阶段，账号信息来自 {@code admin.auth.*} 配置，
 * 不与普通用户的手机号账号、JWT 或角色表共用登录凭据。</p>
 *
 * @param username 后台登录名；服务层会去除首尾空白并按小写形式参与限流和比较
 * @param password 后台明文密码；只在当前请求内参与常量时间比较，不会写入日志或 Redis
 */
public record AdminLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String password
) {
}
