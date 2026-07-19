package com.tongluxing.admin.vo;

import java.time.LocalDateTime;

/** 产品车辆认证审核结果。 */
public record VehicleAuthAdminAuditResponse(
        Long applyId,
        String status,
        String rejectReason,
        Long auditUserId,
        LocalDateTime auditTime,
        Long auditLogId
) {
}
