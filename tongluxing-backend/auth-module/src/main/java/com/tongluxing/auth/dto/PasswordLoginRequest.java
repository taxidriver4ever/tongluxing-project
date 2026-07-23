package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 手机号密码登录请求。 */
public record PasswordLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为6-32位")
        String password,

        String deviceId,

        /** 客户端类型只用于设备审计；所有客户端统一执行账号级单点登录。 */
        @Pattern(regexp = "APP_DRIVER|MERCHANT_WEB|MINI_PROGRAM", message = "客户端类型不正确")
        String clientType
) {
}
