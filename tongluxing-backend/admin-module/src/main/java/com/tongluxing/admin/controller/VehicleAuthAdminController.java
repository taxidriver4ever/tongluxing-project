package com.tongluxing.admin.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.dto.VehicleAuthAdminAuditRequest;
import com.tongluxing.admin.service.AdminAuditService;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.admin.vo.VehicleAuthAdminApplicationVO;
import com.tongluxing.admin.vo.VehicleAuthAdminAuditResponse;
import com.tongluxing.common.result.Result;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditDetailVO;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditSummaryVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 与产品接口定义一致的车辆认证后台联调入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/vehicle/auth")
public class VehicleAuthAdminController {
    private final VehicleService vehicleService;
    private final AdminAuditService adminAuditService;

    /** 查询车辆认证申请；当前阶段登录用户即视为模拟管理员。 */
    @GetMapping("/list")
    public Result<PageResult<VehicleAuthAdminApplicationVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        com.tongluxing.vehicle.vo.PageResult<VehicleCertificationAuditSummaryVO> source =
                vehicleService.pageCertifications(toInternalStatus(status), keyword, page, size);
        List<VehicleAuthAdminApplicationVO> records = source.records().stream()
                .map(VehicleCertificationAuditSummaryVO::certificationId)
                .map(vehicleService::getCertificationForAudit)
                .map(this::toApplication)
                .toList();
        return Result.success(new PageResult<>(records, source.total(), source.page(), source.size()));
    }

    /** 审核通过或拒绝，并同步车辆状态、车辆审计日志、后台审计日志和通知事件。 */
    @PostMapping("/audit")
    public Result<VehicleAuthAdminAuditResponse> audit(
            @Valid @RequestBody VehicleAuthAdminAuditRequest request) {
        String internalStatus = "PASS".equals(request.status()) ? "APPROVED" : "REJECTED";
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? "vehicle-auth-" + request.applyId() + "-" + UUID.randomUUID()
                : request.requestId().trim();
        var result = adminAuditService.auditVehicleCertification(request.applyId(),
                new AdminAuditRequest(internalStatus, request.rejectReason(), requestId));
        return Result.success(new VehicleAuthAdminAuditResponse(
                request.applyId(), toExternalStatus(result.auditResult()), result.rejectReason(),
                result.operatorId(), result.reviewedAt(), result.auditLogId()));
    }

    private VehicleAuthAdminApplicationVO toApplication(VehicleCertificationAuditDetailVO detail) {
        List<String> driverImages = imageUrls(detail, "DRIVER_LICENSE");
        List<String> registrationImages = imageUrls(detail, "REGISTRATION_LICENSE");
        if (registrationImages.isEmpty()) {
            registrationImages = java.util.stream.Stream.of(
                            detail.licenseFrontImageKey(), detail.licenseBackImageKey())
                    .filter(value -> value != null && !value.isBlank()).toList();
        }
        return new VehicleAuthAdminApplicationVO(
                detail.certificationId(), detail.userId(), detail.vehicleId(), detail.vehicleBrand(),
                detail.vehicleModel(), detail.vehicleColor(), detail.plateNo(), driverImages,
                registrationImages, imageUrls(detail, "VEHICLE"), toExternalStatus(detail.status()),
                detail.rejectReason(), detail.submittedAt(), detail.reviewedAt());
    }

    private List<String> imageUrls(VehicleCertificationAuditDetailVO detail, String type) {
        return detail.vehicleImages().stream().filter(image -> type.equals(image.imageType()))
                .map(image -> image.imageKey()).toList();
    }

    private String toInternalStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return switch (status.trim().toUpperCase()) {
            case "PASS" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> status.trim().toUpperCase();
        };
    }

    private String toExternalStatus(String status) {
        return switch (status) {
            case "APPROVED" -> "PASS";
            case "REJECTED" -> "REJECT";
            default -> status;
        };
    }
}
