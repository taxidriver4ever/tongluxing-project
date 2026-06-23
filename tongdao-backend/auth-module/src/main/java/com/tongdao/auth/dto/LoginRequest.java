package com.tongdao.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 手机号验证码登录请求。
 */
public record LoginRequest(
        /** 用户手机号。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        /** 6 位短信验证码。 */
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
        String code,

        /** 设备标识，用于登录日志和多端登录控制。 */
        String deviceId
) {
}
