package com.tongdao.team.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CreateTeamRequest 请求参数对象。
 */
public record CreateTeamRequest(
        @NotNull Long tripId,
        @NotNull Long ownerVehicleId,
        @NotBlank @Size(max = 64) String teamName,
        @Size(max = 255) String teamDesc,
        @NotNull @Min(2) @Max(20) Integer maxMemberCount,
        @Size(max = 20) String joinMode,
        @NotNull Boolean publicFlag,
        @Size(max = 255) String notice
) {
}
