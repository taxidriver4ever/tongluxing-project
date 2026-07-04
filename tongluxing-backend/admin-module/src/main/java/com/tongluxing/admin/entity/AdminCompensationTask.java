package com.tongluxing.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 后台补偿任务实体，对应 admin_compensation_task 表。
 *
 * <p>用于记录需要交给其他业务模块异步承接的后台操作。</p>
 */
@Data
public class AdminCompensationTask {
    /** 主键 ID。 */
    private Long id;
    /** 业务类型。 */
    private String bizType;
    /** 业务 ID。 */
    private String bizId;
    /** 幂等键。 */
    private String idempotentKey;
    /** 目标处理模块。 */
    private String targetModule;
    /** 请求载荷。 */
    private String requestPayload;
    /** 任务状态。 */
    private String taskStatus;
    /** 已重试次数。 */
    private Integer retryCount;
    /** 下次重试时间。 */
    private LocalDateTime nextRetryAt;
    /** 最近一次错误信息。 */
    private String lastError;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
