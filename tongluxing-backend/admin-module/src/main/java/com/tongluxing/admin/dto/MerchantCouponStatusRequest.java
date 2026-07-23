package com.tongluxing.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin 上下架已审核商家券。 */
public record MerchantCouponStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @Size(max = 200) String reason
) {
}
