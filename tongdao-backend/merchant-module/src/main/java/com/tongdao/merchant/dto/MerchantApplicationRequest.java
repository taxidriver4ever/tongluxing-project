package com.tongdao.merchant.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商家入驻申请请求。
 *
 * <p>用于创建待审核商家资料。营业执照、其它资质材料只保存资源 key，不保存文件本体。</p>
 */
public record MerchantApplicationRequest(
        @NotBlank @Size(max = 128) String merchantName,
        @NotBlank @Size(max = 32) String category,
        @NotBlank @Size(max = 64) String contactName,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String contactPhone,
        @Size(max = 16) String provinceCode,
        @Size(max = 16) String cityCode,
        @NotBlank @Size(max = 255) String address,
        BigDecimal longitude,
        BigDecimal latitude,
        @NotBlank @Size(max = 512) String licenseImageKey,
        List<@Size(max = 512) String> qualificationImageKeys,
        @Size(max = 64) String bankAccountNo,
        @Size(max = 128) String bankName
) {
}
