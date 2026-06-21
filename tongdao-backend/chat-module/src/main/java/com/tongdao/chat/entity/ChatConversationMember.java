package com.tongdao.chat.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ChatConversationMember {
    private Long id;
    private Long conversationId;
    private Long userId;
    private String memberRole;
    private String memberStatus;
    private Integer unreadCount;
    private Long lastReadMessageId;
    private LocalDateTime joinedAt;
    private LocalDateTime exitedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
