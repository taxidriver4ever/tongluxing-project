package com.tongluxing.merchant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.dto.MerchantPartnerApplicationRequest;
import com.tongluxing.merchant.dto.MerchantPartnerCancellationRequest;
import com.tongluxing.merchant.service.MerchantPartnerService;
import com.tongluxing.merchant.vo.MerchantPartnerApplicationVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchants/partner-application")
public class MerchantPartnerController {
    private final MerchantPartnerService service;
    @GetMapping public Result<MerchantPartnerApplicationVO> current() { return Result.success(service.current()); }
    @PostMapping public Result<MerchantPartnerApplicationVO> apply(@Valid @RequestBody MerchantPartnerApplicationRequest request) {
        return Result.success(service.apply(request));
    }

    @PostMapping("/cancellation")
    public Result<MerchantPartnerApplicationVO> requestCancellation(
            @Valid @RequestBody MerchantPartnerCancellationRequest request) {
        return Result.success(service.requestCancellation(request));
    }
}
