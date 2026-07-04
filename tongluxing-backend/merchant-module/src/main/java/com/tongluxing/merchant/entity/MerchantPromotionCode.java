package com.tongluxing.merchant.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家推广码实体，对应 merchant_promotion_code 表。
 */
@Data
public class MerchantPromotionCode {
    private Long id;
    private Long merchantId;
    private String promotionCode;
    private String channelName;
    private String scene;
    private String qrImageKey;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
