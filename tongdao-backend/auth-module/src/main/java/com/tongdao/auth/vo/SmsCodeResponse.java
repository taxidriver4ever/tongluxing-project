package com.tongdao.auth.vo;

/**
 * 发送短信验证码响应。
 */
public record SmsCodeResponse(
        /** 验证码有效期，单位秒。 */
        Integer expireSeconds
) {
}
