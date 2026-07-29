package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机号密码登录请求。
 *
 * <p>密码登录只允许已有账号使用，不会因为手机号不存在而自动注册。
 * 服务端使用 BCrypt 校验密码，并复用验证码登录相同的账号级单点登录与设备审计流程。</p>
 *
 * @param phone 已注册的中国大陆手机号
 * @param password 登录明文密码；仅用于 BCrypt 比对，不会持久化或写入日志
 * @param deviceId 可选设备标识；为空时 Token 中记录默认设备标识
 * @param clientType 客户端类型：APP_DRIVER、MERCHANT_WEB 或 MINI_PROGRAM；
 *                   该值用于设备审计，不会划分独立并发会话
 */
public record PasswordLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为6-32位")
        String password,

        /** 设备标识，用于登录日志、设备记录和 JWT 设备声明。 */
        String deviceId,

        /** 客户端类型只用于设备审计；所有客户端统一执行账号级单点登录。 */
        @Pattern(regexp = "APP_DRIVER|MERCHANT_WEB|MINI_PROGRAM", message = "客户端类型不正确")
        String clientType
) {
}
