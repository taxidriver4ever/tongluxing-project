package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建车队群聊会话请求。
 *
 * <p>群主始终取当前登录用户，客户端不能提交 ownerUserId，避免冒充其他队长创建群聊。</p>
 */
public record TeamConversationRequest(
        /** 车队 ID。 */
        @NotNull Long teamId,
        /** 会话名称。 */
        @NotBlank @Size(max = 64) String conversationName
) {
}
