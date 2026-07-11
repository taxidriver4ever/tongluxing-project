package com.tongluxing.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重新计算商家考核请求。
 *
 * @param period 需要重算的考核周期，格式为 yyyy-MM
 * @param reason 重算原因，用于运营审计
 * @param operatorId 操作人 ID
 * @param requestId 幂等请求号，避免重复重算导致结果抖动
 */
public record RecalculateMerchantAssessmentRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long operatorId,
        @NotBlank @Size(max = 128) String requestId) {
}
