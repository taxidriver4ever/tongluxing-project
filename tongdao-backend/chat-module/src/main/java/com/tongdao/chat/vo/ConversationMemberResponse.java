package com.tongdao.chat.vo;

public record ConversationMemberResponse(
        String memberId,
        String conversationId,
        String userId,
        String memberRole,
        String memberStatus,
        Integer unreadCount,
        String joinedAt
) {
}
