package com.tongdao.admin.vo;

import java.time.LocalDateTime;

public record AdminInterventionResultVO(
        Long auditLogId,
        String targetModule,
        String targetType,
        String targetId,
        String action,
        String operationResult,
        String message,
        LocalDateTime operatedAt
) {
}
