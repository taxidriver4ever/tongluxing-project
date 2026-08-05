package com.tongluxing.team.dto;

import jakarta.validation.constraints.Size;

/** 队长移除成员时填写的原因。 */
public record RemoveTeamMemberRequest(@Size(max = 255) String reason) {
}
