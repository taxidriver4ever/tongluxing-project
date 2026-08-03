package com.tongluxing.chat.vo;

/**
 * 当前用户与目标用户之间的私聊权限。
 *
 * <p>单向关注的发起方在对方回复前最多发送三条文字消息；互相关注、同队关系，
 * 或对方已经回复后自动解锁完整聊天能力。</p>
 *
 * @param targetUserId 目标用户 ID
 * @param canStart 当前用户是否具备发起私聊的基础关系
 * @param existingConversationId 已存在的私聊会话 ID，尚未创建时为空
 * @param relationType 双方关系类型，如 MUTUAL、SAME_TRIP、FOLLOWING 或 STRANGER
 * @param remainingTextMessages 未解锁时发起者还可发送的文字消息数量
 * @param canSendMedia 是否允许发送图片或业务卡片
 * @param unlocked 是否已解除陌生人私聊限制
 * @param reason 不允许发起时提供给客户端展示的原因
 */
public record PrivateChatPermissionResponse(
        String targetUserId,
        boolean canStart,
        String existingConversationId,
        String relationType,
        int remainingTextMessages,
        boolean canSendMedia,
        boolean unlocked,
        String reason
) {
}
