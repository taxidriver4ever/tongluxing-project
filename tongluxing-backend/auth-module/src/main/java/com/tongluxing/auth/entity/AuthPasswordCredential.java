package com.tongluxing.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户密码凭证。
 */
@Data
public class AuthPasswordCredential {
    private Long id;
    private Long userId;
    private String passwordHash;
    private String passwordVersion;
    private Integer passwordStatus;
    private LocalDateTime lastSetTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
