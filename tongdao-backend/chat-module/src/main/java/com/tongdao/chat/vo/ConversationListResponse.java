package com.tongdao.chat.vo;

import java.util.List;

public record ConversationListResponse(
        List<ConversationResponse> conversations
) {
}
