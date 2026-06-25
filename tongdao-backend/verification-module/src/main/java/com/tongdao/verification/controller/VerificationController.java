package com.tongdao.verification.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.verification.dto.ConfirmVerificationRequest;
import com.tongdao.verification.dto.CreateVerificationCodeRequest;
import com.tongdao.verification.dto.ParseVerificationRequest;
import com.tongdao.verification.dto.ReversalApplyRequest;
import com.tongdao.verification.dto.VerificationQueryRequest;
import com.tongdao.verification.service.VerificationService;
import com.tongdao.verification.vo.PageResult;
import com.tongdao.verification.vo.VerificationCodeVO;
import com.tongdao.verification.vo.VerificationParseVO;
import com.tongdao.verification.vo.VerificationRecordVO;
import com.tongdao.verification.vo.VerificationReversalVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/verifications")
public class VerificationController {
    private final VerificationService verificationService;

    @PostMapping("/codes")
    public Result<VerificationCodeVO> createCode(@Valid @RequestBody CreateVerificationCodeRequest request) {
        return Result.success(verificationService.createCode(request));
    }

    @PostMapping("/parse")
    public Result<VerificationParseVO> parse(@Valid @RequestBody ParseVerificationRequest request) {
        return Result.success(verificationService.parse(request));
    }

    @PostMapping("/confirm")
    public Result<VerificationRecordVO> confirm(@Valid @RequestBody ConfirmVerificationRequest request) {
        return Result.success(verificationService.confirm(request));
    }

    @GetMapping
    public Result<PageResult<VerificationRecordVO>> pageQuery(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        VerificationQueryRequest request = new VerificationQueryRequest(merchantId, bizType, verificationStatus,
                startTime, endTime, page, size);
        return Result.success(verificationService.pageQuery(request));
    }

    @GetMapping("/{verificationId}")
    public Result<VerificationRecordVO> detail(@PathVariable Long verificationId,
                                               @RequestParam(required = false) Long merchantId) {
        return Result.success(verificationService.detail(verificationId, merchantId));
    }

    @PostMapping("/{verificationId}/reversal")
    public Result<VerificationReversalVO> applyReversal(@PathVariable Long verificationId,
                                                        @Valid @RequestBody ReversalApplyRequest request) {
        return Result.success(verificationService.applyReversal(verificationId, request));
    }
}
