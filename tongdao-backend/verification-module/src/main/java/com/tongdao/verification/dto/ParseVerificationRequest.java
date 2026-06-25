package com.tongdao.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record ParseVerificationRequest(
        @NotBlank String code
) {
}
