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
        /** 最后一条消息预览。 */
        String lastMessagePreview,
        /** 最后一条消息时间。 */
        String lastMessageAt,
        /** 当前用户是否将会话置顶。 */
        boolean pinned,
        /** 当前用户是否开启免打扰。 */
        boolean muted
) {
}
