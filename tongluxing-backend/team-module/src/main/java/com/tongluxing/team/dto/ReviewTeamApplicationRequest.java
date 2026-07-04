package com.tongluxing.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ReviewTeamApplicationRequest 请求参数对象。
 */
public record ReviewTeamApplicationRequest(
        @NotBlank @Size(max = 20) String reviewAction,
        @Size(max = 255) String reviewMessage
) {
}
