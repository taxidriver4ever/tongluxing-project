package com.tongluxing.chat.vo;

/**
 * 会话响应。
 */
public record ConversationResponse(
        /** 会话 ID，使用字符串返回避免前端大整数精度问题。 */
        String conversationId,
        /** 业务类型，例如 TEAM。 */
        String bizType,
        /** 业务 ID。 */
        String bizId,
        /** 会话名称。 */
        String conversationName,
        /** 会话状态。 */
        String conversationStatus,
        /** 第三方 IM 服务商类型。 */
        String providerType,
        /** 腾讯 IM 原始群 ID；私聊为空。 */
        String providerConversationKey,
        /** SDK 使用的会话 ID，例如 group_trip_1 或 c2c_u_10001。 */
        String imConversationId,
        /** 最后一条消息预览。 */
        String lastMessagePreview,
        /** 最后一条消息时间。 */
        String lastMessageAt,
        /** 当前用户是否将会话置顶。 */
        boolean pinned,
        /** 当前用户是否开启免打扰。 */
        boolean muted,
        /** 当前用户未读消息数。 */
        int unreadCount,
        /** 私聊对方或群聊头像对象 Key。 */
        String avatarImageKey,
        /** 已签名的头像访问地址。 */
        String avatarUrl,
        /** 私聊对方用户 ID；群聊为空。 */
        String peerUserId,
        /** STRANGER/FOLLOWING/FOLLOWER/MUTUAL/SAME_TRIP/REPLIED/GROUP。 */
        String relationType,
        /** 单向关注破冰阶段剩余可发文字数。 */
        int remainingTextMessages,
        /** 当前是否允许发送图片等媒体消息。 */
        boolean canSendMedia
) {
}
