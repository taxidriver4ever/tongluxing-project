package com.tongluxing.auth.vo;

/**
 * 发送短信验证码响应。
 *
 * <p>响应故意不返回验证码本身，避免真实短信通道上线后泄露认证因子。</p>
 *
 * @param expireSeconds 验证码剩余有效期，单位秒；不代表发送冷却时间
 */
public record SmsCodeResponse(
        /** 验证码有效期，单位秒。 */
        Integer expireSeconds
) {
}
