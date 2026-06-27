package com.tongdao.admin.vo;

import java.time.LocalDateTime;

/**
 * 后台审计日志响应。
 */
public record AdminAuditLogVO(
        /** 审计日志 ID。 */
        Long id,
        /** 操作人 ID。 */
        Long operatorId,
        /** 操作人展示名称。 */
        String operatorName,
        /** 操作类型。 */
        String actionType,
        /** 目标模块。 */
        String targetModule,
        /** 目标业务类型。 */
        String targetType,
        /** 目标业务 ID。 */
        String targetId,
        /** 请求幂等 ID。 */
        String requestId,
        /** 操作原因。 */
        String operationReason,
        /** 操作结果。 */
        String operationResult,
        /** 操作时间。 */
        LocalDateTime createdAt
) {
}
