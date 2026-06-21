package com.tongdao.team.vo;

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
