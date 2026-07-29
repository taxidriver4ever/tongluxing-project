package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送短信验证码请求。
 *
 * <p>服务端按“场景 + 手机号”维护验证码、六十秒发送冷却和每日发送次数。
 * 当前实现使用 mock 通道，但仍写入发送审计日志，切换真实供应商时接口契约无需变化。</p>
 *
 * @param phone 接收验证码的中国大陆手机号
 * @param scene 验证码用途；为空时规范化为 login，当前仅允许登录场景
 */
public record SmsCodeRequest(
        /** 接收验证码的手机号。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        /** 验证码使用场景；为空时默认按登录场景处理。 */
        String scene
) {
}
