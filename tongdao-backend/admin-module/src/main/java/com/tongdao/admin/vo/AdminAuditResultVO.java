package com.tongdao.admin.vo;

import java.time.LocalDateTime;

/**
 * 后台审核结果响应。
 */
public record AdminAuditResultVO(
        /** 审计日志 ID。 */
        Long auditLogId,
        /** 目标模块。 */
        String targetModule,
        /** 目标业务类型。 */
        String targetType,
        /** 目标业务 ID。 */
        String targetId,
        /** 审核结果。 */
        String auditResult,
        /** 操作结果。 */
        String operationResult,
        /** 返回给运营端的说明文案。 */
        String message,
        /** 操作记录时间。 */
        LocalDateTime operatedAt
) {
}
