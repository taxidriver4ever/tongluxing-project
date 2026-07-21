package com.tongluxing.chat.vo;

import java.util.Map;

/**
 * 消息响应。
 */
public record MessageResponse(
        /** 消息 ID。 */
        String messageId,
        /** 所属会话 ID。 */
        String conversationId,
        /** 发送者用户 ID。 */
        String senderUserId,
        /** 消息类型。 */
        String messageType,
        /** 消息正文内容。 */
        String content,
        /** 发送者公开昵称。 */
        String senderNickname,
        /** 发送者公开头像。 */
        String senderAvatarImageKey,
        /** 结构化消息数据。 */
        Map<String, Object> payload,
        /** 消息状态。 */
        String messageStatus,
        /** 发送时间。 */
        String sentAt
) {
}
