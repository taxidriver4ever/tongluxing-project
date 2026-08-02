package com.tongluxing.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tongluxing.admin.dto.AdminAuditLogQueryRequest;
import com.tongluxing.admin.entity.AdminAuditLog;
import com.tongluxing.admin.mapper.AdminAuditLogMapper;
import com.tongluxing.admin.service.AdminQueryService;
import com.tongluxing.admin.vo.AdminAuditLogVO;
import com.tongluxing.admin.vo.AdminDrivingLicenseDetailVO;
import com.tongluxing.admin.vo.AdminVehicleCertificationDetailVO;
import com.tongluxing.admin.vo.AdminVehicleCertificationImageVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.storage.service.StorageService;
import com.tongluxing.user.vo.DrivingLicenseAuditDetailVO;
import com.tongluxing.user.vo.DrivingLicenseAuditSummaryVO;
import com.tongluxing.user.service.UserService;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditDetailVO;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditSummaryVO;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminQueryServiceImpl implements AdminQueryService {

    /** 审计日志 Mapper。 */
    private final AdminAuditLogMapper auditLogMapper;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final StorageService storageService;

    /** 分页查询审计日志。 */
    @Override
    public PageResult<AdminAuditLogVO> auditLogs(AdminAuditLogQueryRequest request) {
        int page = normalizePage(request.page());
        int size = normalizeSize(request.size());
        int offset = (page - 1) * size;
        List<AdminAuditLogVO> records = auditLogMapper.pageQuery(request.operatorId(), request.actionType(),
                        request.targetModule(), request.targetType(), request.targetId(), request.startTime(),
                        request.endTime(), offset, size)
                .stream()
                .map(this::toVO)
                .toList();
        long total = auditLogMapper.countQuery(request.operatorId(), request.actionType(), request.targetModule(),
                request.targetType(), request.targetId(), request.startTime(), request.endTime());
        return new PageResult<>(records, total, page, size);
    }

    /** 业务列表占位分页；用于接口结构先行，后续逐步接入各模块真实查询。 */
    @Override
    public PageResult<Object> emptyBusinessPage(String module, String status, String keyword,
                                                LocalDateTime startTime, LocalDateTime endTime,
                                                int page, int size) {
        return new PageResult<>(List.of(), 0L, normalizePage(page), normalizeSize(size));
    }

    @Override
    public PageResult<DrivingLicenseAuditSummaryVO> drivingLicenseCertifications(
            String status, String keyword, int page, int size) {
        com.tongluxing.common.model.PageResult<DrivingLicenseAuditSummaryVO> result =
                userService.pageDrivingLicenseCertifications(status, keyword, page, size);
        return new PageResult<>(result.records(), result.total(), result.page(), result.size());
    }

    @Override
    public AdminDrivingLicenseDetailVO drivingLicenseCertificationDetail(Long certificationId) {
        DrivingLicenseAuditDetailVO detail = userService.getDrivingLicenseCertificationForAudit(certificationId);
        return new AdminDrivingLicenseDetailVO(
                detail.certificationId(), detail.userId(), detail.holderName(), detail.licenseNo(), detail.vehicleClass(),
                detail.firstIssueDate(), detail.validFrom(), detail.validTo(), detail.issuingAuthority(),
                detail.licenseFrontImageKey(), presign(detail.licenseFrontImageKey()),
                detail.licenseBackImageKey(), presign(detail.licenseBackImageKey()), detail.recognitionSource(),
                detail.status(), detail.rejectReason(), detail.submittedAt(), detail.reviewedAt());
    }

    @Override
    public PageResult<VehicleCertificationAuditSummaryVO> vehicleCertifications(
            String status, String keyword, int page, int size) {
        com.tongluxing.vehicle.vo.PageResult<VehicleCertificationAuditSummaryVO> result =
                vehicleService.pageCertifications(status, keyword, page, size);
        return new PageResult<>(result.records(), result.total(), result.page(), result.size());
    }

    @Override
    public AdminVehicleCertificationDetailVO vehicleCertificationDetail(Long certificationId) {
        VehicleCertificationAuditDetailVO detail = vehicleService.getCertificationForAudit(certificationId);
        List<AdminVehicleCertificationImageVO> images = detail.vehicleImages().stream()
                .map(image -> new AdminVehicleCertificationImageVO(
                        image.imageType(), image.imageKey(), presign(image.imageKey())))
                .toList();
        return new AdminVehicleCertificationDetailVO(
                detail.certificationId(), detail.vehicleId(), detail.userId(), detail.ownerName(), detail.plateNo(),
                detail.vehicleBrand(), detail.vehicleModel(), detail.vehicleColor(), detail.vehicleType(),
                detail.vin(), detail.engineNo(), detail.registerDate(), detail.issueDate(),
                detail.issuingAuthority(), detail.licenseFrontImageKey(), presign(detail.licenseFrontImageKey()),
                detail.licenseBackImageKey(), presign(detail.licenseBackImageKey()), images, detail.recognitionSource(),
                detail.status(), detail.rejectReason(), detail.submittedAt(), detail.reviewedAt());
    }

    private String presign(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "";
        }
        return storageService.presignDownload(null, null, objectKey).downloadUrl();
    }

    /** 将审计日志实体转换为 VO。 */
    private AdminAuditLogVO toVO(AdminAuditLog log) {
        return new AdminAuditLogVO(log.getId(), log.getOperatorId(), log.getOperatorName(), log.getActionType(),
                log.getTargetModule(), log.getTargetType(), log.getTargetId(), log.getRequestId(),
                log.getOperationReason(), log.getOperationResult(), log.getCreatedAt());
    }

    /** 页码最小为 1。 */
    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /** 每页大小限制在 1 到 100 之间，避免一次查询过大。 */
    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
