package com.tongluxing.invite.event;

import java.time.LocalDateTime;

/**
 * 邀请关系成功落库后的领域事件。
 *
 * <p>由 AFTER_COMMIT 监听器消费，用于触发注册奖励和邀请人数里程碑；
 * 事件发布本身不表示奖励已发放。</p>
 *
 * @param relationId 已提交的邀请关系 ID
 * @param inviterUserId 邀请人用户 ID，也是注册奖励受益人
 * @param inviteeUserId 被邀请人用户 ID
 * @param sourceType 关系绑定来源
 * @param boundAt 关系建立时间
 */
public record InviteRelationBoundEvent(
        Long relationId,
        Long inviterUserId,
        Long inviteeUserId,
        String sourceType,
        LocalDateTime boundAt
) {
}
