package com.tongdao.chat.vo;

public record MessageResponse(
        String messageId,
        String conversationId,
        String senderUserId,
        String messageType,
        String content,
        String messageStatus,
        String sentAt
) {
}
