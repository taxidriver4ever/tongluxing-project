package com.tongluxing.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * App 驾驶端手机号验证码登录请求。
 *
 * <p>该入口与小程序登录共用 {@code auth_account} 中的账号和 {@code userId}。
 * 已存在手机号执行登录；首次出现的手机号会创建账号，并要求提供满足服务端规则的初始密码。
 * 登录成功后会登记设备信息，并按账号级单点登录策略使旧终端会话失效。</p>
 *
 * @param phone 中国大陆手机号，也是当前认证账号的唯一登录标识
 * @param code 六位登录验证码；校验成功后会被删除，不能重复使用
 * @param deviceId App 设备稳定标识；不能为空，用于设备绑定和登录审计
 * @param deviceName 设备展示名称，例如厂商和机型；允许为空
 * @param platform 操作系统或客户端平台；允许为空
 * @param password 首次注册时使用的初始密码；老用户登录时不会覆盖既有密码
 * @param registerSource 可选注册来源；仅新账号创建成功时发布给邀请、商家推广等下游模块
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

        @Size(max = 32, message = "密码长度不能超过32")
        String password,

        @Valid
        RegisterSource registerSource
) {
}
