package com.tongluxing.chat.vo;

/**
 * 会话成员响应。
 */
public record ConversationMemberResponse(
        /** 成员记录 ID。 */
        String memberId,
        /** 会话 ID。 */
        String conversationId,
        /** 用户 ID。 */
        String userId,
        /** 成员角色，例如 OWNER、MEMBER。 */
        String memberRole,
        /** 成员状态，例如 ACTIVE、EXITED。 */
        String memberStatus,
        /** 未读消息数。 */
        Integer unreadCount,
        /** 公开昵称。 */
        String nickname,
        /** 公开头像。 */
        String avatarImageKey,
        /** 当前成员是否免打扰。 */
        Boolean muted,
        /** 当前成员是否置顶。 */
        Boolean pinned,
        /** 加入时间。 */
        String joinedAt
) {
}
