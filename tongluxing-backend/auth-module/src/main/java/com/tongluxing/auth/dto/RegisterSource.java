package com.tongluxing.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 统一注册来源。
 *
 * <p>避免登录请求随业务扩展不断增加 inviteCode、merchantCode、campaignCode 等字段。</p>
 *
 * <p>该对象只在首次创建账号后随 {@code UserRegisteredEvent} 发布。
 * 老用户再次登录时携带来源不会改写首次归因，保证邀请和推广关系不可被后续登录覆盖。</p>
 *
 * @param sourceType 来源类型，例如 INVITE 或 MERCHANT；业务模块自行识别支持的类型
 * @param sourceCode 对应邀请码、推广码或活动码；认证模块只负责规范化和传递
 */
public record RegisterSource(
        /** 注册来源类型；为空表示自然注册。 */
        @Size(max = 32, message = "注册来源类型长度不能超过32")
        String sourceType,

        /** 注册来源编码；与来源类型一起解释。 */
        @Size(max = 64, message = "注册来源标识长度不能超过64")
        String sourceCode
) {
}
