package com.tongdao.chat.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 聊天消息实体，对应 chat_message 表。
 */
@Data
public class ChatMessage {
    /** 主键 ID。 */
    private Long id;
    /** 所属会话 ID。 */
    private Long conversationId;
    /** 发送者用户 ID。 */
    private Long senderUserId;
    /** 消息类型。 */
    private String messageType;
    /** 消息载荷 JSON，目前保存 content 字段。 */
    private String messagePayloadJson;
    /** 消息状态。 */
    private String messageStatus;
    /** 第三方 IM 消息 ID 或本地模拟消息 key。 */
    private String providerMessageKey;
    /** 发送时间。 */
    private LocalDateTime sentAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
