package com.tongluxing.team.vo;

/** 车队详情及 P0 管理设置。 */
public record TeamResponse(
        String teamId,
        String tripId,
        String ownerUserId,
        String ownerVehicleId,
        String teamName,
        String teamDesc,
        String startName,
        String endName,
        String departureTime,
        Integer maxMemberCount,
        Integer currentMemberCount,
        String joinMode,
        String teamStatus,
        Boolean publicFlag,
        String chatConversationId,
        String notice,
        String createdAt,
        String recruitmentStatus,
        Boolean allowMidwayJoin,
        Integer deviationWarningDistanceM,
        Integer deviationWarningMinutes,
        Integer severeDeviationDistanceM,
        Integer severeDeviationMinutes,
        Integer missingLocationMinutes,
        Integer joinRadiusM,
        String privacyLevel,
        Boolean currentUserOwner,
        Boolean currentUserMember
) {
}
