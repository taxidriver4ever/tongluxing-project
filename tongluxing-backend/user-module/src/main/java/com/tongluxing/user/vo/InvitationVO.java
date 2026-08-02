package com.tongluxing.user.vo;

import java.time.LocalDateTime;

/**
 * 单条邀请关系返回对象。
 *
 * @param relationId 邀请关系主键
 * @param inviteeUserId 被邀请人用户 ID
 * @param inviteCode 绑定时使用的邀请码
 * @param status 关系状态
 * @param boundAt 绑定时间
 * @param firstTeamCompletedAt 被邀请人首次完成组队时间
 */
public record InvitationVO(
        Long relationId, Long inviteeUserId, String inviteCode, String status,
        LocalDateTime boundAt, LocalDateTime firstTeamCompletedAt
) {
}

