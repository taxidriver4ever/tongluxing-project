package com.tongluxing.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 队长对脱队/失联异常执行忽略或移除。 */
public record HandleTeamAlertRequest(
        @NotBlank @Pattern(regexp = "IGNORE|REMOVE") String action
) {
}
