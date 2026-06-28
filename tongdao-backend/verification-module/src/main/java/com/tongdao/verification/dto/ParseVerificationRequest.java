package com.tongdao.verification.dto;

import jakarta.validation.constraints.NotBlank;
/**
 * ParseVerificationRequest 请求对象。
 */

public record ParseVerificationRequest(
        @NotBlank String code
) {
}
