package com.tongluxing.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 双端设备绑定关系。
 */
@Data
public class AuthDeviceBinding {
    private Long id;
    private Long userId;
    private String phone;
    private String clientType;
    private String deviceId;
    private String deviceName;
    private String platform;
    private Integer bindStatus;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
