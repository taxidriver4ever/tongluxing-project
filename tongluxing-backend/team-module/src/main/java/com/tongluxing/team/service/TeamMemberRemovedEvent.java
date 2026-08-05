package com.tongluxing.team.service;

/** 队长移除成员后同步聊天群、行程成员和系统推送。 */
public record TeamMemberRemovedEvent(
        Long teamId,
        Long tripId,
        Long memberUserId,
        Long operatorUserId,
        String reason
) {
}
