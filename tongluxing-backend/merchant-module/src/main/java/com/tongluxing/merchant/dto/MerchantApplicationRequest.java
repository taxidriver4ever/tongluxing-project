package com.tongluxing.merchant.dto;

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
        @NotBlank @Size(max = 64) String merchantShortName,
        @NotBlank @Size(max = 512) String logoImageKey,
        @NotBlank @Size(max = 1000) String introduction,
        @NotBlank @Size(max = 32) String category,
        @NotBlank @Size(max = 512) String businessLicenseImageKey,
        @NotBlank @Size(max = 64) String contactName,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String contactPhone,
        @NotBlank @Size(max = 128) String contactEmail,
        @Size(max = 64) String contactWechat,
        @NotBlank @Size(max = 128) String storeName,
        @NotBlank @Size(max = 255) String storeAddress,
        BigDecimal longitude,
        BigDecimal latitude,
        @NotBlank @Size(max = 512) String storefrontImageKey,
        @Size(min = 3, max = 6) List<@Size(max = 512) String> interiorImageKeys,
        @NotBlank @Size(max = 64) String legalRepresentativeName,
        @NotBlank @Size(max = 512) String legalIdFrontImageKey,
        @NotBlank @Size(max = 512) String legalIdBackImageKey,
        List<@Size(max = 512) String> qualificationImageKeys
) {
}
