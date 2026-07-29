package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * 小程序手机号验证码登录请求。
 *
 * <p>手机号不存在时会在同一事务中创建认证账号；手机号已存在时仅更新登录时间和设备信息。
 * 验证码校验通过后才进入公共登录流程，成功后验证码与历史失败计数都会清除。</p>
 *
 * @param phone 中国大陆手机号，作为账号唯一登录标识
 * @param code 六位短信验证码，有效期和错误次数均由 Redis 控制
 * @param deviceId 可选设备标识，用于审计及 JWT 的设备声明；为空时服务端使用默认值
 * @param password 首次注册时设置的初始密码；已有账号不会因此覆盖密码
 * @param registerSource 可选注册来源，仅首次注册时向业务模块发布
 */
public record LoginRequest(
        /** 用户手机号；格式在进入 Controller 前由 Bean Validation 校验。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        /** 六位短信验证码；校验成功后立即失效。 */
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^$|^\\d{6}$", message = "验证码格式不正确")
        String code,

        /** 设备标识，用于 JWT 声明、登录日志和设备审计。 */
        String deviceId,

        /** 首次注册时必填，用于设置登录密码。 */
        @Size(max = 32, message = "密码长度不能超过32")
        String password,

        /** 统一注册来源，仅首次注册成功后随 UserRegisteredEvent 传递给业务模块。 */
        @Valid
        RegisterSource registerSource
) {
}
