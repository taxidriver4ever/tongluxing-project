package com.tongluxing.invite.event;

import java.time.LocalDateTime;

/** 邀请关系成功落库后的领域事件。 */
public record InviteRelationBoundEvent(
        Long relationId,
        Long inviterUserId,
        Long inviteeUserId,
        String sourceType,
        LocalDateTime boundAt
) {
}
