package com.tongluxing.payment.gateway;

import org.springframework.stereotype.Component;

import com.tongluxing.common.utils.SnowflakeIdGenerator;

/**
 * 本地支付网关实现。
 *
 * <p>该类集中承载未接入第三方支付时的可预测 mock 数据，避免 mock 字段散落在
 * PaymentService 中。返回值形态与真实微信支付参数保持一致，便于后续替换。</p>
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String APP_ID = "mock-app-id";
    private static final String SIGN_TYPE = "RSA";

    @Override
    public PrepayResult createPrepay(PrepayCommand command) {
        String prepayId = "mock_prepay_" + SnowflakeIdGenerator.nextIdString();
        return new PrepayResult(APP_ID, String.valueOf(System.currentTimeMillis() / 1000),
                SnowflakeIdGenerator.nextIdString(), "prepay_id=" + prepayId, SIGN_TYPE,
                "mock-pay-sign", prepayId);
    }

    @Override
    public RefundResult requestRefund(RefundCommand command) {
        String refundId = "mock_refund_" + SnowflakeIdGenerator.nextIdString();
        return new RefundResult(refundId, "{\"gateway\":\"MOCK\",\"status\":\"ACCEPTED\"}");
    }

    @Override
    public ProfitSharingResult createProfitSharing(ProfitSharingCommand command) {
        String sharingId = "mock_sharing_" + SnowflakeIdGenerator.nextIdString();
        return new ProfitSharingResult(sharingId, "{\"gateway\":\"MOCK\",\"status\":\"SUCCESS\"}");
    }
}

