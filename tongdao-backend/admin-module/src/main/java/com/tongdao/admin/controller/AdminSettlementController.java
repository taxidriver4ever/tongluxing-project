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

/**
 * 运营后台结算管理接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/settlements")
public class AdminSettlementController {

    /** 后台审核/触发类动作服务。 */
    private final AdminAuditService auditService;
    /** 后台通用查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询结算单；当前返回空分页，后续可接入 payment-module 查询端口。 */
    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("payment-module", status, null, null, null, page, size));
    }

    /** 触发指定结算单处理。 */
    @PostMapping("/{settlementId}/trigger")
    public Result<AdminAuditResultVO> trigger(@PathVariable Long settlementId,
                                              @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(auditService.triggerSettlement(settlementId, request));
    }
}
