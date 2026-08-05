package com.tongluxing.team.vo;

/** 车队成员；隐私字段由服务层按队伍设置脱敏。 */
public record TeamMemberResponse(
        String memberId,
        String teamId,
        String userId,
        String vehicleId,
        String memberRole,
        String memberStatus,
        String nicknameSnapshot,
        String vehicleSnapshot,
        String joinedAt,
        String linkedOwnerUserId,
        String linkedVehicleId,
        String plateReference,
        String ownerConfirmStatus,
        Boolean canRemove,
        Boolean self
) {
}
