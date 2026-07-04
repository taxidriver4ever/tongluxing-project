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
 * 运营后台商家入驻审核接口。
 *
 * <p>当前列表查询为占位实现，审核动作会记录审计日志并生成补偿任务。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/merchants/applications")
public class AdminMerchantAuditController {

    /** 后台审核服务。 */
    private final AdminAuditService auditService;
    /** 后台通用查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询商家入驻申请；当前返回空分页，后续可接入 merchant-module 查询端口。 */
    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("merchant-module", status, null, null, null, page, size));
    }

    /** 审核指定商家入驻申请。 */
    @PostMapping("/{applicationId}/audit")
    public Result<AdminAuditResultVO> audit(@PathVariable Long applicationId,
                                            @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(auditService.auditMerchantApplication(applicationId, request));
    }
}
