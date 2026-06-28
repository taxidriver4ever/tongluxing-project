package com.tongdao.verification.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
/**
 * VerificationRecord 数据库实体。
 */

@Data
public class VerificationRecord {
    private Long id;
    private Long verificationCodeId;
    private String verificationCode;
    private String bizType;
    private Long bizId;
    private Long orderId;
    private Long userCouponId;
    private Long userId;
    private Long merchantId;
    private Long operatorId;
    private BigDecimal amount;
    private String verificationStatus;
    private String locationName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime verifiedAt;
    private String reversalStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
