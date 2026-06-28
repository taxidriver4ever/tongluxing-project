package com.tongdao.payment.service;

import com.tongdao.payment.dto.JsapiPaymentRequest;
import com.tongdao.payment.dto.PaymentCallbackRequest;
import com.tongdao.payment.dto.ProfitSharingRequest;
import com.tongdao.payment.dto.RefundApplyRequest;
import com.tongdao.payment.dto.RefundCallbackRequest;
import com.tongdao.payment.vo.JsapiPayParamsVO;
import com.tongdao.payment.vo.ProfitSharingVO;
import com.tongdao.payment.vo.RefundVO;

/**
 * 支付模块业务服务接口。
 */
public interface PaymentService {

    /**
     * 创建微信 JSAPI 支付会话。
     */
    JsapiPayParamsVO createJsapiPayment(JsapiPaymentRequest request);

    /**
     * 处理支付渠道回调。
     */
    void handlePaymentCallback(PaymentCallbackRequest request);

    /**
     * 创建退款申请。
     */
    RefundVO applyRefund(RefundApplyRequest request);

    /**
     * 查询退款详情。
     */
    RefundVO refundDetail(Long refundId);

    /**
     * 处理退款渠道回调。
     */
    void handleRefundCallback(RefundCallbackRequest request);

    /**
     * 核销后执行分账。
     */
    ProfitSharingVO shareAfterVerification(ProfitSharingRequest request);
}
