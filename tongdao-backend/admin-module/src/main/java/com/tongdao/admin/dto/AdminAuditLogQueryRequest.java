package com.tongdao.admin.dto;

import java.time.LocalDateTime;

public record AdminAuditLogQueryRequest(
        Long operatorId,
        String actionType,
        String targetModule,
        String targetType,
        String targetId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int page,
        int size
) {
}
