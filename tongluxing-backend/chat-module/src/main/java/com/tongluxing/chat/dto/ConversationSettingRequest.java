package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 封装会话设置请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 *
 * @param muted 是否开启消息免打扰
 * @param pinned 是否把会话置顶
 */
public record ConversationSettingRequest(@NotNull Boolean muted, @NotNull Boolean pinned) {
}
