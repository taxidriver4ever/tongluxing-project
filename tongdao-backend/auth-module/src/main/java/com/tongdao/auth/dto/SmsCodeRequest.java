package com.tongdao.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送短信验证码请求。
 */
public record SmsCodeRequest(
        /** 接收验证码的手机号。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        /** 验证码使用场景；为空时默认按登录场景处理。 */
        String scene
) {
}
