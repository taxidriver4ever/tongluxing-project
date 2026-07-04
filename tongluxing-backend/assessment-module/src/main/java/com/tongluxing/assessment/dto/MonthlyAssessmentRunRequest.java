package com.tongluxing.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 月度考核批处理请求。
 */
public record MonthlyAssessmentRunRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotNull Long operatorId,
        @NotBlank @Size(max = 128) String requestId) {
}
