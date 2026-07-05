package com.tongluxing.assessment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 人工调整商家考核分请求。
 */
public record ManualAssessmentAdjustmentRequest(
        @NotNull BigDecimal scoreDelta,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long operatorId,
        @NotBlank @Size(max = 128) String requestId
) {
}
