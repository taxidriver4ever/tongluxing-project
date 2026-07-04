package com.tongluxing.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
/**
 * JsapiPaymentRequest 请求对象。
 */

public record JsapiPaymentRequest(@NotNull Long orderId, @NotBlank String openId) {
}

