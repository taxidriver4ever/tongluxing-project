package com.tongluxing.application.adapter;

import org.springframework.stereotype.Component;

import com.tongluxing.payment.dto.ProfitSharingRequest;
import com.tongluxing.payment.service.PaymentService;
import com.tongluxing.verification.integration.VerificationPaymentPort;

import lombok.RequiredArgsConstructor;

/**
 * 核销模块触发支付分账的应用层适配器。
 *
 * <p>实现 verification-module 定义的支付端口，在核销成功后调用 payment-module 发起分账。</p>
 */
@Component
@RequiredArgsConstructor
public class VerificationPaymentAdapter implements VerificationPaymentPort {

    private final PaymentService paymentService;

    /**
     * 核销完成后触发支付模块分账。
     *
     * @param orderId 订单 ID
     * @param merchantId 商家 ID
     * @param verificationId 核销记录 ID
     * @param requestId 幂等请求号
     */
    @Override
    public void shareAfterVerification(Long orderId, Long merchantId, Long verificationId, String requestId) {
        paymentService.shareAfterVerification(new ProfitSharingRequest(orderId, verificationId, merchantId, requestId));
    }
}
