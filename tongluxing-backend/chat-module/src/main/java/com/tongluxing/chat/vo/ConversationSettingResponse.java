package com.tongluxing.chat.vo;

/**
 * 会话设置对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 */
public record ConversationSettingResponse(String conversationId, Boolean muted, Boolean pinned) {
}
