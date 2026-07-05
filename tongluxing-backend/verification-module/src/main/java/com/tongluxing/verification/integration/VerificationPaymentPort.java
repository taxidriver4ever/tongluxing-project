package com.tongluxing.verification.integration;

/**
 * 核销模块触发支付分账的端口。
 */
public interface VerificationPaymentPort {

    void shareAfterVerification(Long orderId, Long merchantId, Long verificationId, String requestId);
}
