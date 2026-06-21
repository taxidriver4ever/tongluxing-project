package com.tongdao.user.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.user.model.UserModels.CouponDeductionVO;
import com.tongdao.user.model.UserModels.CouponLockRequest;
import com.tongdao.user.model.UserModels.CouponOrderResultRequest;
import com.tongdao.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/user-coupons")
public class UserInternalController {
    private final UserService userService;

    @PostMapping("/{id}/lock")
    public Result<CouponDeductionVO> lock(@PathVariable Long id, @Valid @RequestBody CouponLockRequest request) {
        return Result.success(userService.lockCoupon(id, request));
    }

    @PostMapping("/order-result")
    public Result<Void> orderResult(@Valid @RequestBody CouponOrderResultRequest request) {
        userService.handleOrderResult(request);
        return Result.success();
    }
}
