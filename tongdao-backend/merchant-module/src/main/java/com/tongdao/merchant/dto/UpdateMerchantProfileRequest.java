package com.tongdao.merchant.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改当前商家资料请求。
 *
 * <p>字段均为可选，未传入表示保留原值。资质类关键字段变更由后续 admin 审核流程处理。</p>
 */
public record UpdateMerchantProfileRequest(
        @Size(max = 128) String merchantName,
        @Size(max = 32) String category,
        @Size(max = 64) String contactName,
        @Pattern(regexp = "^1[3-9]\\d{9}$") String contactPhone,
        @Size(max = 255) String address,
        BigDecimal longitude,
        BigDecimal latitude,
        @Size(max = 512) String coverImageKey,
        @Size(max = 1000) String description
) {
}
