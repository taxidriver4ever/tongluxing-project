package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * 手机号验证码登录请求。
 */
public record LoginRequest(
        /** 用户手机号。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        /** 6 位短信验证码。 */
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^$|^\\d{6}$", message = "验证码格式不正确")
        String code,

        /** 设备标识，用于登录日志和多端登录控制。 */
        String deviceId,

        /** 首次注册时必填，用于设置登录密码。 */
        @Size(max = 32, message = "密码长度不能超过32")
        String password,

        /** 统一注册来源，仅首次注册成功后随 UserRegisteredEvent 传递给业务模块。 */
        @Valid
        RegisterSource registerSource
) {
}
