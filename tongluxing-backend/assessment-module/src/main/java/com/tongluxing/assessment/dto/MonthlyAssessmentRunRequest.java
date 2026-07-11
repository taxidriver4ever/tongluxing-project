package com.tongluxing.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 月度考核批处理请求。
 *
 * @param period 考核周期，格式为 yyyy-MM
 * @param operatorId 触发人 ID，系统任务固定为 0
 * @param requestId 幂等请求号，用于避免同一周期重复执行
 */
public record MonthlyAssessmentRunRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotNull Long operatorId,
        @NotBlank @Size(max = 128) String requestId) {
}
