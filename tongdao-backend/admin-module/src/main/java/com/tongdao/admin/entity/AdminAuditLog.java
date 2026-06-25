package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminAuditLog {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String actionType;
    private String targetModule;
    private String targetType;
    private String targetId;
    private String requestId;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String operationReason;
    private String operationResult;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
