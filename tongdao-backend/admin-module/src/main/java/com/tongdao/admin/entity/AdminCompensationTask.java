package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminCompensationTask {
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
