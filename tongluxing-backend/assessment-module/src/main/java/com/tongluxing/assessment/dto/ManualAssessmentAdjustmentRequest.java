package com.tongluxing.assessment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 人工调整商家考核分请求。
 *
 * @param scoreDelta 调整分值，正数表示加分，负数表示扣分
 * @param reason 调整原因，用于运营审计和后续复盘
 * @param operatorId 操作人 ID
 * @param requestId 幂等请求号，避免重复提交造成重复加扣分
 */
public record ManualAssessmentAdjustmentRequest(
        @NotNull BigDecimal scoreDelta,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long operatorId,
        @NotBlank @Size(max = 128) String requestId
) {
}
