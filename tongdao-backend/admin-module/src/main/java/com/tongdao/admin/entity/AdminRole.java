package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminRole {
    private Long id;
    private String roleCode;
    private String roleName;
    private String permissionJson;
    private String roleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
