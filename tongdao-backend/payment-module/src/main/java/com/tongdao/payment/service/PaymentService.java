package com.tongdao.payment.service;

import com.tongdao.payment.dto.JsapiPaymentRequest;
import com.tongdao.payment.dto.PaymentCallbackRequest;
import com.tongdao.payment.dto.ProfitSharingRequest;
import com.tongdao.payment.dto.RefundApplyRequest;
import com.tongdao.payment.dto.RefundCallbackRequest;
import com.tongdao.payment.vo.JsapiPayParamsVO;
import com.tongdao.payment.vo.ProfitSharingVO;
import com.tongdao.payment.vo.RefundVO;

public interface PaymentService {
    JsapiPayParamsVO createJsapiPayment(JsapiPaymentRequest request);

    void handlePaymentCallback(PaymentCallbackRequest request);

    RefundVO applyRefund(RefundApplyRequest request);

    RefundVO refundDetail(Long refundId);

    void handleRefundCallback(RefundCallbackRequest request);

    ProfitSharingVO shareAfterVerification(ProfitSharingRequest request);
}

