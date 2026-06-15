package com.tongdao.user.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserIdentityCertification {

    private Long id;
    private Long userId;
    private String realName;
    private String idCardNoCipher;
    private String idCardNoMask;
    private String faceImageKey;
    private String status;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
