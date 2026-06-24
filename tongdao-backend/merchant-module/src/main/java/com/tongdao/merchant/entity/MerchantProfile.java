package com.tongdao.merchant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家基础资料实体，对应 merchant_profile 表。
 */
@Data
public class MerchantProfile {
    private Long id;
    private Long userId;
    private String merchantName;
    private String category;
    private String contactName;
    private String contactPhoneCipher;
    private String contactPhoneMask;
    private String provinceCode;
    private String cityCode;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coverImageKey;
    private String description;
    private String licenseImageKey;
    private String qualificationJson;
    private String bankAccountCipher;
    private String bankName;
    private String auditStatus;
    private String merchantLevel;
    private BigDecimal score;
    private BigDecimal commissionRate;
    private BigDecimal rankWeight;
    private BigDecimal exclusionRadiusKm;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
