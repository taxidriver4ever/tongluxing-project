package com.tongdao.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
/**
 * RefundApplyRequest 请求对象。
 */

public record RefundApplyRequest(
        @NotNull Long orderId,
        @NotBlank @Size(max = 255) String reason,
        @NotBlank @Size(max = 128) String requestId
) {
}

