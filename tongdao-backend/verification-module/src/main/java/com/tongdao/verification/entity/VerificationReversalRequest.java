package com.tongdao.verification.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class VerificationReversalRequest {
    private Long id;
    private Long verificationId;
    private Long merchantId;
    private Long applicantId;
    private String reason;
    private String auditStatus;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
