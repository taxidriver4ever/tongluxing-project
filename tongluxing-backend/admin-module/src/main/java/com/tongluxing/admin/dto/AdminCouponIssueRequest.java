package com.tongluxing.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 运营人员按用户 ID 人工发券。requestId 作为幂等业务号。 */
public record AdminCouponIssueRequest(
        @NotNull Long userId,
        @NotNull Long templateId,
        @NotBlank @Size(min = 2, max = 120) String reason,
        @NotBlank @Size(max = 64) String requestId) {
}
