package com.tongluxing.chat.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatJoinApplication {
    private Long id;
    private Long conversationId;
    private Long applicantUserId;
    private String applicationMessage;
    private String applicationStatus;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
