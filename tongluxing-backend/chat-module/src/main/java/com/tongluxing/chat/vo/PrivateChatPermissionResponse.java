package com.tongluxing.chat.vo;

/**
 * 当前用户与目标用户之间的私聊权限。
 *
 * <p>单向关注的发起方在对方回复前最多发送三条文字消息；互相关注、同队关系，
 * 或对方已经回复后自动解锁完整聊天能力。</p>
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
