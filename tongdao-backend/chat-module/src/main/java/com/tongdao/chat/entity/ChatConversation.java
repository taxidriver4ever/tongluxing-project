package com.tongdao.chat.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ChatConversation {
    private Long id;
    private String bizType;
    private Long bizId;
    private String conversationName;
    private String conversationStatus;
    private String providerType;
    private String providerConversationKey;
    private Long lastMessageId;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
