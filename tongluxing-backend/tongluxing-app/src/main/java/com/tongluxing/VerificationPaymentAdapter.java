package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.payment.dto.ProfitSharingRequest;
import com.tongluxing.payment.service.PaymentService;
import com.tongluxing.verification.integration.VerificationPaymentPort;

import lombok.RequiredArgsConstructor;

/**
 * 核销模块触发支付分账的应用层适配器。
 */
@Component
@RequiredArgsConstructor
public class VerificationPaymentAdapter implements VerificationPaymentPort {

    private final PaymentService paymentService;

    @Override
    public void shareAfterVerification(Long orderId, Long merchantId, Long verificationId, String requestId) {
        paymentService.shareAfterVerification(new ProfitSharingRequest(orderId, merchantId, verificationId, requestId));
    }
}
