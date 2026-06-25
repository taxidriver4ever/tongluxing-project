package com.tongdao.verification.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class VerificationCode {
    private Long id;
    private String verificationCode;
    private String qrContent;
    private String bizType;
    private Long bizId;
    private Long orderId;
    private Long userCouponId;
    private Long userId;
    private Long merchantId;
    private BigDecimal amount;
    private String codeStatus;
    private LocalDateTime expireAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
