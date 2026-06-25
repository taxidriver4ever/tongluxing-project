package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminOperator {
    private Long id;
    private String username;
    private String displayName;
    private String phone;
    private String passwordHash;
    private String operatorStatus;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
