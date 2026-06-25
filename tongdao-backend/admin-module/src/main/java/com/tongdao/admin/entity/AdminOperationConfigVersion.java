package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminOperationConfigVersion {
    private Long id;
    private Long configId;
    private String configDomain;
    private String configKey;
    private Integer versionNo;
    private String configValue;
    private LocalDateTime effectiveAt;
    private Long operatorId;
    private String changeReason;
    private LocalDateTime createdAt;
}
