package com.tongdao.payment.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.payment.dto.JsapiPaymentRequest;
import com.tongdao.payment.dto.PaymentCallbackRequest;
import com.tongdao.payment.dto.ProfitSharingRequest;
import com.tongdao.payment.dto.RefundApplyRequest;
import com.tongdao.payment.dto.RefundCallbackRequest;
import com.tongdao.payment.service.PaymentService;
import com.tongdao.payment.vo.JsapiPayParamsVO;
import com.tongdao.payment.vo.ProfitSharingVO;
import com.tongdao.payment.vo.RefundVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/jsapi")
    public Result<JsapiPayParamsVO> jsapi(@Valid @RequestBody JsapiPaymentRequest request) {
        return Result.success(paymentService.createJsapiPayment(request));
    }

    @PostMapping("/callback")
    public Result<Void> paymentCallback(@Valid @RequestBody PaymentCallbackRequest request) {
        paymentService.handlePaymentCallback(request);
        return Result.success();
    }

    @PostMapping("/refunds")
    public Result<RefundVO> refund(@Valid @RequestBody RefundApplyRequest request) {
        return Result.success(paymentService.applyRefund(request));
    }

    @GetMapping("/refunds/{refundId}")
    public Result<RefundVO> refundDetail(@PathVariable Long refundId) {
        return Result.success(paymentService.refundDetail(refundId));
    }

    @PostMapping("/refunds/callback")
    public Result<Void> refundCallback(@Valid @RequestBody RefundCallbackRequest request) {
        paymentService.handleRefundCallback(request);
        return Result.success();
    }

    @PostMapping("/profit-sharing/after-verification")
    public Result<ProfitSharingVO> shareAfterVerification(@Valid @RequestBody ProfitSharingRequest request) {
        return Result.success(paymentService.shareAfterVerification(request));
    }
}

