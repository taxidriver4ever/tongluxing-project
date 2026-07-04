package com.tongluxing.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重新计算商家考核请求。
 */
public record RecalculateMerchantAssessmentRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long operatorId,
        @NotBlank @Size(max = 128) String requestId) {
}
