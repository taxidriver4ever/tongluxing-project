package com.tongdao.admin.vo;

import java.time.LocalDateTime;

public record AdminAuditResultVO(
        Long auditLogId,
        String targetModule,
        String targetType,
        String targetId,
        String auditResult,
        String operationResult,
        String message,
        LocalDateTime operatedAt
) {
}
