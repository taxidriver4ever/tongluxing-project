package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminOperationConfig {
    private Long id;
    private String configDomain;
    private String configKey;
    private Integer currentVersion;
    private String configStatus;
    private LocalDateTime effectiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
