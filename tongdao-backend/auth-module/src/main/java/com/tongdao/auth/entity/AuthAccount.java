package com.tongdao.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AuthAccount {

    private Long id;

    private Long userId;

    private String phone;

    private Integer accountStatus;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
