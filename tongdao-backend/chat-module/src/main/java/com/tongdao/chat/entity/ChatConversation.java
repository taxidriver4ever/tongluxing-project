package com.tongdao.chat.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 聊天会话实体，对应 chat_conversation 表。
 *
 * <p>一条会话关联一个业务对象，例如车队群聊关联 TEAM + teamId。</p>
 */
@Data
public class ChatConversation {
    /** 主键 ID。 */
    private Long id;
    /** 业务类型，例如 TEAM。 */
    private String bizType;
    /** 业务 ID。 */
    private Long bizId;
    /** 会话名称。 */
    private String conversationName;
    /** 会话状态。 */
    private String conversationStatus;
    /** 第三方 IM 服务商类型。 */
    private String providerType;
    /** 第三方 IM 会话或群组 ID。 */
    private String providerConversationKey;
    /** 最后一条消息 ID。 */
    private Long lastMessageId;
    /** 最后一条消息预览。 */
    private String lastMessagePreview;
    /** 最后一条消息时间。 */
    private LocalDateTime lastMessageAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
