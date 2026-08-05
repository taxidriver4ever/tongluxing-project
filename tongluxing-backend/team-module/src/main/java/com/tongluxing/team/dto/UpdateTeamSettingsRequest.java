package com.tongluxing.team.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** 队长维护招募、途中加入、脱队阈值和隐私设置。 */
public record UpdateTeamSettingsRequest(
        @Pattern(regexp = "OPEN|PAUSED|CLOSED") String recruitmentStatus,
        Boolean allowMidwayJoin,
        @Min(1000) @Max(500000) Integer deviationWarningDistanceM,
        @Min(1) @Max(1440) Integer deviationWarningMinutes,
        @Min(1000) @Max(1000000) Integer severeDeviationDistanceM,
        @Min(1) @Max(2880) Integer severeDeviationMinutes,
        @Min(10) @Max(10080) Integer missingLocationMinutes,
        @Min(1000) @Max(500000) Integer joinRadiusM,
        @Pattern(regexp = "OPEN|STANDARD|PRIVATE") String privacyLevel
) {
}
