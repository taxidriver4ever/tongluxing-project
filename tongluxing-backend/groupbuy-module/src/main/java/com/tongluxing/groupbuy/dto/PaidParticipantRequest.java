package com.tongluxing.groupbuy.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 支付成功后写入拼团参与人的请求。
 *
 * @param orderId 支付成功的订单 ID
 * @param userId 参团用户 ID
 * @param paidAt 支付完成时间；为空时服务端使用当前时间
 * @param requestId 客户端或支付模块生成的幂等请求号
 */
public record PaidParticipantRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        LocalDateTime paidAt,
        @NotBlank String requestId
) {
}

