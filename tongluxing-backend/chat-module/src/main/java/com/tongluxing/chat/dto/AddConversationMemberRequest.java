package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 添加会话成员请求。
 */
public record AddConversationMemberRequest(
        /** 要加入会话的用户 ID。 */
        @NotNull Long userId
) {
}
