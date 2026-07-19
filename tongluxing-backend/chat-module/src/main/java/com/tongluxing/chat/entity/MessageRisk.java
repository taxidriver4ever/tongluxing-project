package com.tongluxing.chat.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 命中安全规则的消息风险记录；与普通聊天查询隔离。 */
@Data
public class MessageRisk {
    private Long id;
    private Long messageId;
    private String riskLevel;
    private String riskType;
    private Integer confidence;
    private String status;
    private String matchedRule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
