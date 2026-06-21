package com.tongdao.coupon.controller;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.tongdao.common.result.Result;
import com.tongdao.coupon.integration.CouponFacade.CouponIssueResult;
import com.tongdao.coupon.service.CouponService;
import com.tongdao.user.model.UserModels.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CouponController {
    private final CouponService service;

    @GetMapping("/v1/coupons/me")
    public Result<PageResult<CouponSummaryVO>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.currentCoupons(status, type, page, size));
    }

    @GetMapping("/v1/coupons/me/{id}")
    public Result<UserCouponDetailVO> detail(@PathVariable Long id) { return Result.success(service.currentCoupon(id)); }

    @GetMapping("/v1/coupons/available")
    public Result<List<AvailableCouponVO>> available(@RequestParam String orderType,
                                                     @RequestParam(required = false) Long merchantId,
                                                     @RequestParam BigDecimal amount) {
        return Result.success(service.available(orderType, merchantId, amount));
    }

    @PostMapping("/v1/coupons/templates/{id}/claim")
    public Result<CouponIssueResult> claim(@PathVariable Long id) { return Result.success(service.claim(id)); }

    @PostMapping("/internal/v1/coupons/issues")
    public Result<CouponIssueResult> issue(@RequestBody IssueRequest request) {
        return Result.success(service.issue(request.userId(), request.templateId(), request.sourceType(), request.sourceBizId()));
    }

    @PostMapping("/internal/v1/coupons/{id}/lock")
    public Result<CouponDeductionVO> lock(@PathVariable Long id, @RequestBody CouponLockRequest request) {
        return Result.success(service.lock(id, request));
    }

    @PostMapping("/internal/v1/coupons/order-result")
    public Result<Void> result(@RequestBody CouponOrderResultRequest request) {
        service.orderResult(request);
        return Result.success();
    }

    public record IssueRequest(Long userId, Long templateId, String sourceType, String sourceBizId) {}
}
