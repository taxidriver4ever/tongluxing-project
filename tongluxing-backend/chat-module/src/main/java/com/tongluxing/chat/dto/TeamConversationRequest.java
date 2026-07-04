package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建车队群聊会话请求。
 */
public record TeamConversationRequest(
        /** 车队 ID。 */
        @NotNull Long teamId,
        /** 群主用户 ID。 */
        @NotNull Long ownerUserId,
        /** 会话名称。 */
        @NotBlank @Size(max = 64) String conversationName
) {
}
