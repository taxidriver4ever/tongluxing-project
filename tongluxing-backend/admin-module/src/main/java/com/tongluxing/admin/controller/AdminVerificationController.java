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
import com.tongluxing.admin.service.AdminAuditService;
import com.tongluxing.admin.service.AdminQueryService;
import com.tongluxing.admin.vo.AdminAuditResultVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 运营后台核销撤销审核接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/verifications/reversals")
public class AdminVerificationController {

    /** 后台审核服务。 */
    private final AdminAuditService auditService;
    /** 后台通用查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询核销撤销申请；当前返回空分页，后续可接入 verification-module 查询端口。 */
    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("verification-module", status, null, null, null, page, size));
    }

    /** 审核指定核销撤销申请。 */
    @PostMapping("/{reversalId}/audit")
    public Result<AdminAuditResultVO> audit(@PathVariable Long reversalId,
                                            @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(auditService.auditVerificationReversal(reversalId, request));
    }
}
