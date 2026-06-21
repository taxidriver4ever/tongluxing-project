package com.tongdao.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewTeamApplicationRequest(
        @NotBlank @Size(max = 20) String reviewAction,
        @Size(max = 255) String reviewMessage
) {
}
