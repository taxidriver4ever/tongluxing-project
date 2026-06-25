package com.tongdao.admin.vo;

import java.time.LocalDateTime;

public record AdminAuditLogVO(
        Long id,
        Long operatorId,
        String operatorName,
        String actionType,
        String targetModule,
        String targetType,
        String targetId,
        String requestId,
        String operationReason,
        String operationResult,
        LocalDateTime createdAt
) {
}
