package com.tongluxing.chat.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 聊天会话成员实体，对应 chat_conversation_member 表。
 */
@Data
public class ChatConversationMember {
    /** 主键 ID。 */
    private Long id;
    /** 会话 ID。 */
    private Long conversationId;
    /** 用户 ID。 */
    private Long userId;
    /** 成员角色，例如 OWNER、MEMBER。 */
    private String memberRole;
    /** 成员状态，例如 ACTIVE、EXITED。 */
    private String memberStatus;
    /** 未读消息数。 */
    private Integer unreadCount;
    /** 是否开启消息免打扰。 */
    private Boolean mutedFlag;
    /** 是否将会话置顶。 */
    private Boolean pinnedFlag;
    /** 最近已读消息 ID。 */
    private Long lastReadMessageId;
    /** 加入时间。 */
    private LocalDateTime joinedAt;
    /** 退出时间。 */
    private LocalDateTime exitedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
