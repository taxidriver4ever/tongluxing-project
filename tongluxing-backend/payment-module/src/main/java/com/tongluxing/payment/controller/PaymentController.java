package com.tongluxing.payment.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.payment.dto.JsapiPaymentRequest;
import com.tongluxing.payment.dto.PaymentCallbackRequest;
import com.tongluxing.payment.dto.ProfitSharingRequest;
import com.tongluxing.payment.dto.RefundApplyRequest;
import com.tongluxing.payment.dto.RefundCallbackRequest;
import com.tongluxing.payment.service.PaymentService;
import com.tongluxing.payment.vo.JsapiPayParamsVO;
import com.tongluxing.payment.vo.ProfitSharingVO;
import com.tongluxing.payment.vo.RefundVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 支付模块接口控制器，提供支付、退款和分账入口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    /**
     * 创建微信小程序 JSAPI 支付参数。
     */
    @PostMapping("/jsapi")
    public Result<JsapiPayParamsVO> jsapi(@Valid @RequestBody JsapiPaymentRequest request) {
        return Result.success(paymentService.createJsapiPayment(request));
    }

    /**
     * 处理支付成功回调，并推进订单与拼团状态。
     */
    @PostMapping("/callback")
    public Result<Void> paymentCallback(@Valid @RequestBody PaymentCallbackRequest request) {
        paymentService.handlePaymentCallback(request);
        return Result.success();
    }

    /**
     * 发起用户退款申请。
     */
    @PostMapping("/refunds")
    public Result<RefundVO> refund(@Valid @RequestBody RefundApplyRequest request) {
        return Result.success(paymentService.applyRefund(request));
    }

    /**
     * 查询退款记录详情。
     */
    @GetMapping("/refunds/{refundId}")
    public Result<RefundVO> refundDetail(@PathVariable Long refundId) {
        return Result.success(paymentService.refundDetail(refundId));
    }

    /**
     * 处理微信退款成功回调。
     */
    @PostMapping("/refunds/callback")
    public Result<Void> refundCallback(@Valid @RequestBody RefundCallbackRequest request) {
        paymentService.handleRefundCallback(request);
        return Result.success();
    }

    /**
     * 券码核销后触发商家分账。
     */
    @PostMapping("/profit-sharing/after-verification")
    public Result<ProfitSharingVO> shareAfterVerification(@Valid @RequestBody ProfitSharingRequest request) {
        return Result.success(paymentService.shareAfterVerification(request));
    }
}
