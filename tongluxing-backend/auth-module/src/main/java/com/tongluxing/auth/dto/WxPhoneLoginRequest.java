package com.tongluxing.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * 微信小程序手机号授权登录请求。
 */
public record WxPhoneLoginRequest(
        /** 微信小程序端获取到的一次性手机号授权 code。 */
        String code,

        /** 联调阶段模拟手机号；传入后不会调用微信接口。 */
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "模拟手机号格式不正确")
        String mockPhone,

        /** 设备标识，用于登录日志和多端登录控制。 */
        String deviceId,

        /** 首次注册时必填，用于设置登录密码。 */
        @Size(max = 32, message = "密码长度不能超过32")
        String password,

        /** 统一注册来源，仅首次注册成功后随 UserRegisteredEvent 传递给业务模块。 */
        @Valid
        RegisterSource registerSource,

        /** 联调便捷字段，等价于 registerSource={sourceType:INVITE,sourceCode:...}。 */
        @Size(max = 16, message = "邀请码长度不能超过16")
        String inviteCode
) {
}
