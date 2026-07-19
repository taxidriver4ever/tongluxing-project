package com.tongluxing.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinApplicationReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision) {
}
