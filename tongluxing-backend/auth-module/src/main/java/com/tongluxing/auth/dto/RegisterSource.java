package com.tongluxing.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 统一注册来源。
 *
 * <p>避免登录请求随业务扩展不断增加 inviteCode、merchantCode、campaignCode 等字段。</p>
 */
public record RegisterSource(
        @Size(max = 32, message = "注册来源类型长度不能超过32")
        String sourceType,

        @Size(max = 64, message = "注册来源标识长度不能超过64")
        String sourceCode
) {
}
