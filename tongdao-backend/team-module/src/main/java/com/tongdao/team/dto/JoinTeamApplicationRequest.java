package com.tongdao.team.dto;

import jakarta.validation.constraints.Size;

public record JoinTeamApplicationRequest(
        Long applicantVehicleId,
        @Size(max = 255) String applyMessage,
        String joinQuestionJson
) {
}
