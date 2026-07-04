package com.tongluxing.chat.vo;

import java.util.List;

/**
 * 消息列表响应。
 */
public record MessageListResponse(
        /** 消息列表。 */
        List<MessageResponse> messages
) {
}
