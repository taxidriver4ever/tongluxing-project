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

/**
 * 负责管理端商家合作相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/merchant-partners/applications")
public class AdminMerchantPartnerController {
    private final MerchantPartnerService service;
    private final CurrentUserContext currentUser;

    /** 执行 page 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping public Result<PageResult<MerchantPartnerApplicationVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.applications(status, page, size);
        return Result.success(new PageResult<>(result.records(), result.total(), result.page(), result.size()));
    }

    /** 执行 detail 对应的领域操作，并返回统一的业务结果。 */
    @GetMapping("/{merchantId}") public Result<MerchantPartnerApplicationVO> detail(@PathVariable Long merchantId) {
        return Result.success(service.detail(merchantId));
    }

    /** 执行 audit 对应的领域操作，并返回统一的业务结果。 */
    @PostMapping("/{merchantId}/audit") public Result<MerchantPartnerApplicationVO> audit(
            @PathVariable Long merchantId, @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(service.audit(merchantId, request.auditResult(), request.rejectReason(),
                currentUser.requireUserId()));
    }

    @PostMapping("/{merchantId}/cancellation-audit")
    /** 执行 auditCancellation 对应的领域操作，并返回统一的业务结果。 */
    public Result<MerchantPartnerApplicationVO> auditCancellation(
            @PathVariable Long merchantId, @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(service.auditCancellation(merchantId, request.auditResult(), request.rejectReason(),
                currentUser.requireUserId()));
    }
}
