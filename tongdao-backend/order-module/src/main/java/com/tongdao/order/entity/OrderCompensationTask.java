package com.tongdao.order.entity;

import java.time.LocalDateTime;

import lombok.Data;
/**
 * OrderCompensationTask 数据库实体。
 */

@Data
public class OrderCompensationTask {
    private Long id;
    private String bizType;
    private String bizId;
    private String idempotentKey;
    private String targetModule;
    private String requestPayload;
    private String taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

