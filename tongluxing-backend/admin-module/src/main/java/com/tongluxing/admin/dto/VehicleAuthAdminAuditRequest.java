package com.tongluxing.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 产品车辆认证后台审核请求，使用 PASS/REJECT 状态。 */
public record VehicleAuthAdminAuditRequest(
        @NotNull Long applyId,
        @NotBlank @Pattern(regexp = "PASS|REJECT") String status,
        @Size(max = 255) String rejectReason,
        @Size(max = 128) String requestId
) {
}
