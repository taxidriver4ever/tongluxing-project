package com.tongluxing.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.service.AdminTradeService;
import com.tongluxing.admin.vo.AdminRefundVO;
import com.tongluxing.admin.vo.AdminAuditResultVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 运营后台退款审核接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/refunds")
public class AdminRefundController {

    /** 后台审核服务。 */
    private final AdminTradeService tradeService;

    /** 分页查询退款单；当前返回空分页，后续可接入 payment-module 查询端口。 */
    @GetMapping
    public Result<PageResult<AdminRefundVO>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(tradeService.refunds(status,page,size));
    }

    @GetMapping("/{refundId}") public Result<AdminRefundVO> detail(@PathVariable Long refundId){return Result.success(tradeService.refund(refundId));}

    /** 审核指定退款申请。 */
    @PostMapping("/{refundId}/audit")
    public Result<AdminAuditResultVO> audit(@PathVariable Long refundId,
                                            @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(tradeService.auditRefund(refundId, request));
    }
}
