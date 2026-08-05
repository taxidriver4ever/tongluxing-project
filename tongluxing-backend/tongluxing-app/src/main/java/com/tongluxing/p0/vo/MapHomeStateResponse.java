package com.tongluxing.p0.vo;

import java.util.List;
import java.util.Map;

import com.tongluxing.team.vo.TeamResponse;
import com.tongluxing.trip.vo.ArrivalDecisionResponse;
import com.tongluxing.trip.vo.TripResponse;

/** 地图首页状态机：普通、出发前预览、行程中。 */
public record MapHomeStateResponse(
        String mode,
        TripResponse trip,
        TeamResponse team,
        Long departureCountdownSeconds,
        String chatConversationId,
        List<MapMemberPositionResponse> memberPositions,
        List<Map<String, Object>> alerts,
        List<Map<String, Object>> departureExceptions,
        ArrivalDecisionResponse arrival
) {
}
