package com.tongluxing.chat.vo;

import java.util.List;

/**
 * 会话列表响应。
 */
public record ConversationListResponse(
        /** 当前用户参与的会话列表。 */
        List<ConversationResponse> conversations
) {
}
