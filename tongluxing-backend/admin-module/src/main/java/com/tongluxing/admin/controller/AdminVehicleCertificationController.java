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
import com.tongluxing.admin.vo.AdminVehicleCertificationDetailVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditSummaryVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 运营后台车辆认证审核接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/vehicles/certifications")
public class AdminVehicleCertificationController {
    private final AdminQueryService queryService;
    private final AdminAuditService auditService;

    @GetMapping
    public Result<PageResult<VehicleCertificationAuditSummaryVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.vehicleCertifications(status, keyword, page, size));
    }

    @GetMapping("/{certificationId}")
    public Result<AdminVehicleCertificationDetailVO> detail(@PathVariable Long certificationId) {
        return Result.success(queryService.vehicleCertificationDetail(certificationId));
    }

    @PostMapping("/{certificationId}/audit")
    public Result<AdminAuditResultVO> audit(
            @PathVariable Long certificationId,
            @Valid @RequestBody AdminAuditRequest request) {
        return Result.success(auditService.auditVehicleCertification(certificationId, request));
    }
}
