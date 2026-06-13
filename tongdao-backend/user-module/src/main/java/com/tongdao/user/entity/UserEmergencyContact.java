package com.tongdao.user.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserEmergencyContact {

    private Long id;
    private Long userId;
    private String contactName;
    private String relation;
    private String phoneCipher;
    private String phoneMask;
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
