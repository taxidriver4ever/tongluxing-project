package com.tongluxing.chat.vo;

/**
 * 会话设置对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 *
 * @param conversationId 会话 ID，字符串形式避免前端大整数精度损失
 * @param muted 当前用户是否开启免打扰
 * @param pinned 当前用户是否置顶该会话
 */
public record ConversationSettingResponse(String conversationId, Boolean muted, Boolean pinned) {
}
