package com.tongluxing.team.dto;

import jakarta.validation.constraints.Size;

/**
 * JoinTeamApplicationRequest 请求参数对象。
 */
public record JoinTeamApplicationRequest(
        Long applicantVehicleId,
        @Size(max = 255) String applyMessage,
        String joinQuestionJson
) {
}
