package com.tongluxing.admin.vo;

import java.time.LocalDateTime;

/**
 * 后台人工干预结果响应。
 */
public record AdminInterventionResultVO(
        /** 审计日志 ID。 */
        Long auditLogId,
        /** 目标模块。 */
        String targetModule,
        /** 目标业务类型。 */
        String targetType,
        /** 目标业务 ID。 */
        String targetId,
        /** 干预动作。 */
        String action,
        /** 操作结果。 */
        String operationResult,
        /** 返回给运营端的说明文案。 */
        String message,
        /** 操作记录时间。 */
        LocalDateTime operatedAt
) {
}
