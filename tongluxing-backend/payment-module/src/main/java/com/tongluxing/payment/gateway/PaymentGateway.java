package com.tongluxing.payment.gateway;

import java.math.BigDecimal;

/**
 * 支付网关抽象。
 *
 * <p>业务服务只依赖该接口，不直接拼接 mock 字符串。当前未接入微信支付时，
 * 使用本地 Mock 实现；后续接入真实微信支付只需要新增 Gateway 实现。</p>
 */
public interface PaymentGateway {

    PrepayResult createPrepay(PrepayCommand command);

    RefundResult requestRefund(RefundCommand command);

    ProfitSharingResult createProfitSharing(ProfitSharingCommand command);

    record PrepayCommand(Long orderId, String orderNo, BigDecimal amount) {
    }

    record PrepayResult(String appId, String timeStamp, String nonceStr, String packageValue,
                        String signType, String paySign, String prepayId) {
    }

    record RefundCommand(Long refundId, String refundNo, Long orderId, BigDecimal amount, String reason) {
    }

    record RefundResult(String externalRefundId, String rawPayload) {
    }

    record ProfitSharingCommand(Long orderId, Long merchantId, Long verificationId,
                                BigDecimal totalAmount, BigDecimal merchantAmount) {
    }

    record ProfitSharingResult(String externalSharingId, String rawPayload) {
    }
}
