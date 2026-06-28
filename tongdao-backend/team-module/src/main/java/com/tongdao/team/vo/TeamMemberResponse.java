package com.tongdao.team.vo;

/**
 * TeamMemberResponse 响应数据对象。
 */
public record TeamMemberResponse(
        String memberId,
        String teamId,
        String userId,
        String vehicleId,
        String memberRole,
        String memberStatus,
        String nicknameSnapshot,
        String vehicleSnapshot,
        String joinedAt
) {
}
