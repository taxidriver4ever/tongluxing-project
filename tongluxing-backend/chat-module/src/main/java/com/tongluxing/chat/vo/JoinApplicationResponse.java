package com.tongluxing.chat.vo;

public record JoinApplicationResponse(
        String applicationId, String conversationId, String conversationName,
        String applicantUserId, String nickname, String avatarImageKey,
        String applicationMessage, String status, String createdAt) {
}
