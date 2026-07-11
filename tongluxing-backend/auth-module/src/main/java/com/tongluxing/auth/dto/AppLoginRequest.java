package com.tongluxing.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * App 驾驶端手机号验证码登录请求。
 */
public record AppLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^$|^\\d{6}$", message = "验证码格式不正确")
        String code,

        @NotBlank(message = "设备标识不能为空")
        String deviceId,

        String deviceName,

        String platform,

        @Valid
        RegisterSource registerSource
) {
}
