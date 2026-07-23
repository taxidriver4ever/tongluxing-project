package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.merchant.service.MerchantPartnerService;
import com.tongluxing.merchant.vo.MerchantPartnerApplicationVO;
import com.tongluxing.user.support.CurrentUserContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/merchant-partners/applications")
public class AdminMerchantPartnerController {
    private final MerchantPartnerService service;
    private final CurrentUserContext currentUser;

    @GetMapping public Result<PageResult<MerchantPartnerApplicationVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.applications(status, page, size);
        return Result.success(new PageResult<>(result.records(), result.total(), result.page(), result.size()));
    }

    @GetMapping("/{merchantId}") public Result<MerchantPartnerApplicationVO> detail(@PathVariable Long merchantId) {
        return Result.success(service.detail(merchantId));
    }

    @PostMapping("/{merchantId}/audit") public Result<MerchantPartnerApplicationVO> audit(
            @PathVariable Long merchantId, @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(service.audit(merchantId, request.auditResult(), request.rejectReason(),
                currentUser.requireUserId()));
    }

    @PostMapping("/{merchantId}/cancellation-audit")
    public Result<MerchantPartnerApplicationVO> auditCancellation(
            @PathVariable Long merchantId, @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(service.auditCancellation(merchantId, request.auditResult(), request.rejectReason(),
                currentUser.requireUserId()));
    }
}
