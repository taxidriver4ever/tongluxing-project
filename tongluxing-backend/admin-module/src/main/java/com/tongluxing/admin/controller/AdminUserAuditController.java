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
import com.tongluxing.admin.vo.AdminDrivingLicenseDetailVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditSummaryVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 运营后台用户认证审核接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/users/certifications")
public class AdminUserAuditController {

    /** 后台审核服务。 */
    private final AdminAuditService auditService;
    /** 后台通用查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询驾驶证认证申请。 */
    @GetMapping
    public Result<PageResult<DrivingLicenseAuditSummaryVO>> page(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.drivingLicenseCertifications(status, keyword, page, size));
    }

    /** 查询驾驶证认证审核详情。 */
    @GetMapping("/{certificationId}")
    public Result<AdminDrivingLicenseDetailVO> detail(@PathVariable Long certificationId) {
        return Result.success(queryService.drivingLicenseCertificationDetail(certificationId));
    }

    /** 审核指定驾驶证认证申请。 */
    @PostMapping("/{certificationId}/audit")
    public Result<AdminAuditResultVO> audit(@PathVariable Long certificationId,
                                            @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(auditService.auditUserCertification(certificationId, request));
    }
}
