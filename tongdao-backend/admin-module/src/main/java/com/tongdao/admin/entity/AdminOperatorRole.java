package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminOperatorRole {
    private Long id;
    private Long operatorId;
    private Long roleId;
    private LocalDateTime createdAt;
    private Integer deleted;
}
