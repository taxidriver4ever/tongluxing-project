package com.tongluxing.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * 微信小程序手机号授权登录请求。
 *
 * <p>正式环境通过微信一次性 code 换取手机号；联调环境可传 {@code mockPhone}
 * 跳过微信网络调用。两者最终进入同一手机号登录流程，因此账号创建、密码初始化、
 * 注册来源、设备审计和单点登录规则完全一致。</p>
 *
 * @param code {@code wx.getPhoneNumber} 返回的一次性授权 code
 * @param mockPhone 仅用于联调的模拟手机号；存在时优先使用且不调用微信接口
 * @param deviceId 小程序设备标识，用于登录日志和 Token 设备声明
 * @param password 首次注册时的初始密码；已有账号不会被覆盖
 * @param registerSource 结构化注册来源，只在首次注册事件中生效
 * @param inviteCode 兼容旧前端的邀请码快捷字段；仅在 registerSource 为空时转换为 INVITE 来源
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
