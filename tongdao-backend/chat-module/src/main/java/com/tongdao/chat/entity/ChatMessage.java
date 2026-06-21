package com.tongdao.chat.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ChatMessage {
    private Long id;
    private Long conversationId;
    private Long senderUserId;
    private String messageType;
    private String messagePayloadJson;
    private String messageStatus;
    private String providerMessageKey;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
