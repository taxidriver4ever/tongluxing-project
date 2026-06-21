package com.tongdao.chat.vo;

public record ConversationResponse(
        String conversationId,
        String bizType,
        String bizId,
        String conversationName,
        String conversationStatus,
        String providerType,
        String lastMessagePreview,
        String lastMessageAt
) {
}
