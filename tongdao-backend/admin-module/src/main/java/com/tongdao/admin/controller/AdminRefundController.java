package com.tongdao.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.admin.dto.AdminAuditRequest;
import com.tongdao.admin.service.AdminAuditService;
import com.tongdao.admin.service.AdminQueryService;
import com.tongdao.admin.vo.AdminAuditResultVO;
import com.tongdao.admin.vo.PageResult;
import com.tongdao.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/refunds")
public class AdminRefundController {
    private final AdminAuditService auditService;
    private final AdminQueryService queryService;

    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("payment-module", status, null, null, null, page, size));
    }

    @PostMapping("/{refundId}/audit")
    public Result<AdminAuditResultVO> audit(@PathVariable Long refundId,
                                            @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(auditService.auditRefund(refundId, request));
    }
}
