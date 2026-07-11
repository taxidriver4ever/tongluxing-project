package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

/**
 * 微信小程序手机号授权登录请求。
 */
public record WxPhoneLoginRequest(
        /** 微信小程序端获取到的一次性手机号授权 code。 */
        @NotBlank(message = "微信手机号授权 code 不能为空")
        String code,

        /** 设备标识，用于登录日志和多端登录控制。 */
        String deviceId,

        /** 统一注册来源，仅首次注册成功后随 UserRegisteredEvent 传递给业务模块。 */
        @Valid
        RegisterSource registerSource
) {
}
