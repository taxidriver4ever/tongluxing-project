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

/**
 * 负责商家合作相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchants/partner-application")
public class MerchantPartnerController {
    private final MerchantPartnerService service;
    /** 执行 current 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping public Result<MerchantPartnerApplicationVO> current() { return Result.success(service.current()); }
    /** 执行 apply 对应的领域操作，并返回统一的业务结果。 */
    @PostMapping public Result<MerchantPartnerApplicationVO> apply(@Valid @RequestBody MerchantPartnerApplicationRequest request) {
        return Result.success(service.apply(request));
    }

    @PostMapping("/cancellation")
    /** 执行 requestCancellation 对应的领域操作，并返回统一的业务结果。 */
    public Result<MerchantPartnerApplicationVO> requestCancellation(
            @Valid @RequestBody MerchantPartnerCancellationRequest request) {
        return Result.success(service.requestCancellation(request));
    }
}
