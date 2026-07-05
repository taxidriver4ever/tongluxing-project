package com.tongluxing.verification.controller;

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

import com.tongluxing.common.result.Result;
import com.tongluxing.verification.dto.ConfirmVerificationRequest;
import com.tongluxing.verification.dto.CreateVerificationCodeRequest;
import com.tongluxing.verification.dto.ParseVerificationRequest;
import com.tongluxing.verification.dto.ReversalApplyRequest;
import com.tongluxing.verification.dto.VerificationQueryRequest;
import com.tongluxing.verification.service.VerificationService;
import com.tongluxing.verification.vo.PageResult;
import com.tongluxing.verification.vo.VerificationCodeVO;
import com.tongluxing.verification.vo.VerificationParseVO;
import com.tongluxing.verification.vo.VerificationRecordVO;
import com.tongluxing.verification.vo.VerificationReversalVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 券核销模块接口控制器，提供核销码生成、解析、确认核销、查询和冲正申请能力。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/verifications")
public class VerificationController {
    private final VerificationService verificationService;

    /**
     * 为订单或优惠券生成核销码。
     */
    @PostMapping("/codes")
    public Result<VerificationCodeVO> createCode(@Valid @RequestBody CreateVerificationCodeRequest request) {
        return Result.success(verificationService.createCode(request));
    }

    /**
     * 扫码后解析核销码，返回是否可核销及阻断原因。
     */
    @PostMapping("/parse")
    public Result<VerificationParseVO> parse(@Valid @RequestBody ParseVerificationRequest request) {
        return Result.success(verificationService.parse(request));
    }

    /**
     * 商家确认核销。
     */
    @PostMapping("/confirm")
    public Result<VerificationRecordVO> confirm(@Valid @RequestBody ConfirmVerificationRequest request) {
        return Result.success(verificationService.confirm(request));
    }

    /**
     * 分页查询核销记录。
     */
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

    /**
     * 查询单条核销记录详情。
     */
    @GetMapping("/{verificationId}")
    public Result<VerificationRecordVO> detail(@PathVariable Long verificationId,
                                               @RequestParam(required = false) Long merchantId) {
        return Result.success(verificationService.detail(verificationId, merchantId));
    }

    /**
     * 对已核销记录提交冲正申请。
     */
    @PostMapping("/{verificationId}/reversal")
    public Result<VerificationReversalVO> applyReversal(@PathVariable Long verificationId,
                                                        @Valid @RequestBody ReversalApplyRequest request) {
        return Result.success(verificationService.applyReversal(verificationId, request));
    }

    /**
     * 内部消费核销补偿任务，用于核销后触发本地分账或券状态同步。
     */
    @PostMapping("/internal/compensation-tasks/process")
    public Result<Integer> processCompensationTasks(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(verificationService.processCompensationTasks(limit));
    }
}
