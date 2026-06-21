package com.tongdao.team.vo;

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
        String createdAt
) {
}
