package com.tongluxing.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 审核通过后提交的收款账户，不属于首轮入驻资料。 */
public record MerchantSettlementRequest(
        @NotBlank @Pattern(regexp = "^(CORPORATE|LEGAL_PERSON)$") String accountType,
        @NotBlank @Size(max = 128) String accountName,
        @NotBlank @Size(max = 64) String accountNo,
        @NotBlank @Size(max = 128) String bankName
) {}
