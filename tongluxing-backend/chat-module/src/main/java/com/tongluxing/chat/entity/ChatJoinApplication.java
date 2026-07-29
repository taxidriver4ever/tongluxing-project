package com.tongluxing.chat.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 描述聊天加入申请持久化记录及其当前业务状态。
 * 对象由 MyBatis 在数据库行与 Java 字段之间进行映射。
 */
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
