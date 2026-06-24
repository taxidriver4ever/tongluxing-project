package com.tongdao.merchant.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家操作审计实体，对应 merchant_audit_log 表。
 */
@Data
public class MerchantAuditLog {
    private Long id;
    private Long merchantId;
    private Long operatorId;
    private String operationType;
    private String targetType;
    private Long targetId;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String remark;
    private LocalDateTime createdAt;
}
