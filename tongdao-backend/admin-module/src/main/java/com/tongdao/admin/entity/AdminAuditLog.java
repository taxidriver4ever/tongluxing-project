package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 后台审计日志实体，对应 admin_audit_log 表。
 */
@Data
public class AdminAuditLog {
    /** 主键 ID。 */
    private Long id;
    /** 操作人 ID。 */
    private Long operatorId;
    /** 操作人展示名称。 */
    private String operatorName;
    /** 操作类型。 */
    private String actionType;
    /** 目标模块。 */
    private String targetModule;
    /** 目标业务类型。 */
    private String targetType;
    /** 目标业务 ID。 */
    private String targetId;
    /** 请求幂等 ID。 */
    private String requestId;
    /** 操作前快照。 */
    private String beforeSnapshot;
    /** 操作后快照或请求载荷。 */
    private String afterSnapshot;
    /** 操作原因。 */
    private String operationReason;
    /** 操作结果。 */
    private String operationResult;
    /** 操作 IP。 */
    private String ip;
    /** 操作端 User-Agent。 */
    private String userAgent;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
