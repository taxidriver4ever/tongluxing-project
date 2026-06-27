package com.tongdao.admin.dto;

import java.time.LocalDateTime;

/**
 * 后台审计日志分页查询条件。
 */
public record AdminAuditLogQueryRequest(
        /** 操作人 ID。 */
        Long operatorId,
        /** 操作类型。 */
        String actionType,
        /** 目标模块。 */
        String targetModule,
        /** 目标业务类型。 */
        String targetType,
        /** 目标业务 ID。 */
        String targetId,
        /** 查询开始时间。 */
        LocalDateTime startTime,
        /** 查询结束时间。 */
        LocalDateTime endTime,
        /** 页码，从 1 开始。 */
        int page,
        /** 每页条数。 */
        int size
) {
}
