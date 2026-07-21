package com.tongluxing.admin.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.entity.AdminAuditLog;
import com.tongluxing.admin.mapper.AdminAuditLogMapper;
import com.tongluxing.admin.service.AdminAuditService;
import com.tongluxing.admin.vo.AdminAuditResultVO;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.notify.dto.CreateNotificationEventRequest;
import com.tongluxing.notify.service.NotificationService;
import com.tongluxing.notify.service.NotificationEventTypes;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditDetailVO;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditDetailVO;

import lombok.RequiredArgsConstructor;

/** 后台审核服务实现。 */
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {
    private static final String SUCCESS = "SUCCESS";

    private final AdminAuditLogMapper auditLogMapper;
    private final AdminSupport support;
    private final CurrentUserContext currentUserContext;
    private final StringRedisTemplate redis;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final NotificationService notificationService;
    private final MerchantService merchantService;

    /** 驾驶证人工审核，同步更新 user-module 事实状态。 */
    @Override
    @Transactional
    public AdminAuditResultVO auditUserCertification(Long certificationId, AdminAuditRequest request) {
        String auditResult = normalizeAuditResult(request.auditResult());
        String rejectReason = normalizeRejectReason(auditResult, request.rejectReason());
        Long operatorId = currentUserContext.requireUserId();
        String type = "DRIVING_LICENSE";
        String idemKey = idemKey(type, request.requestId());
        AdminAuditResultVO repeated = repeatedResult(idemKey, request.requestId());
        if (repeated != null) {
            return repeated;
        }
        String lockKey = "admin:lock:audit:%s:%d".formatted(type, certificationId);
        String lockValue = operatorId + ":" + UUID.randomUUID();
        lock(lockKey, lockValue);
        try {
            DrivingLicenseAuditDetailVO before = userService.getDrivingLicenseCertificationForAudit(certificationId);
            DrivingLicenseAuditDetailVO after = userService.applyDrivingLicenseAuditResult(
                    certificationId, auditResult, rejectReason, operatorId);
            AdminAuditLog log = support.audit("DRIVING_LICENSE_AUDIT", "user-module",
                    "DRIVING_LICENSE_CERTIFICATION", String.valueOf(certificationId), request.requestId(),
                    operatorId, rejectReason, SUCCESS,
                    certificationSnapshot(before.status(), before.rejectReason()),
                    certificationSnapshot(after.status(), rejectReason));
            notifyDrivingLicense(after, auditResult, rejectReason, request.requestId());
            AdminAuditResultVO result = completedResult(log, auditResult, rejectReason, operatorId, after.reviewedAt());
            support.writeJson(idemKey, result, Duration.ofHours(24));
            support.deleteRedis("admin:cert:pending-count:DRIVING_LICENSE");
            return result;
        } finally {
            unlock(lockKey, lockValue);
        }
    }

    /** 车辆认证人工审核，同步更新认证记录和车辆档案状态。 */
    @Override
    @Transactional
    public AdminAuditResultVO auditVehicleCertification(Long certificationId, AdminAuditRequest request) {
        String auditResult = normalizeAuditResult(request.auditResult());
        String rejectReason = normalizeRejectReason(auditResult, request.rejectReason());
        Long operatorId = currentUserContext.requireUserId();
        String type = "VEHICLE_CERT";
        String idemKey = idemKey(type, request.requestId());
        AdminAuditResultVO repeated = repeatedResult(idemKey, request.requestId());
        if (repeated != null) {
            return repeated;
        }
        String lockKey = "admin:lock:audit:%s:%d".formatted(type, certificationId);
        String lockValue = operatorId + ":" + UUID.randomUUID();
        lock(lockKey, lockValue);
        try {
            VehicleCertificationAuditDetailVO before = vehicleService.getCertificationForAudit(certificationId);
            VehicleCertificationAuditDetailVO after = vehicleService.applyCertificationAuditResult(
                    certificationId, auditResult, rejectReason, operatorId);
            AdminAuditLog log = support.audit("VEHICLE_CERT_AUDIT", "vehicle-module", "VEHICLE_CERTIFICATION",
                    String.valueOf(certificationId), request.requestId(), operatorId, rejectReason, SUCCESS,
                    certificationSnapshot(before.status(), before.rejectReason()),
                    certificationSnapshot(after.status(), rejectReason));
            notifyVehicle(after, auditResult, rejectReason, request.requestId());
            AdminAuditResultVO result = completedResult(log, auditResult, rejectReason, operatorId, after.reviewedAt());
            support.writeJson(idemKey, result, Duration.ofHours(24));
            support.deleteRedis("admin:cert:pending-count:VEHICLE_CERT");
            return result;
        } finally {
            unlock(lockKey, lockValue);
        }
    }

    @Override
    @Transactional
    public AdminAuditResultVO auditMerchantApplication(Long applicationId, AdminAuditRequest request) {
        String auditResult = normalizeAuditResult(request.auditResult());
        String rejectReason = normalizeRejectReason(auditResult, request.rejectReason());
        Long operatorId = currentUserContext.requireUserId();
        String type = "MERCHANT";
        String idemKey = idemKey(type, request.requestId());
        AdminAuditResultVO repeated = repeatedResult(idemKey, request.requestId());
        if (repeated != null) return repeated;
        String lockKey = "admin:lock:audit:%s:%d".formatted(type, applicationId);
        String lockValue = operatorId + ":" + UUID.randomUUID();
        lock(lockKey, lockValue);
        try {
            var before = merchantService.applicationForAdmin(applicationId);
            var after = merchantService.auditApplication(applicationId, auditResult, rejectReason, operatorId);
            AdminAuditLog log = support.audit("MERCHANT_AUDIT", "merchant-module", "MERCHANT_APPLICATION",
                    String.valueOf(applicationId), request.requestId(), operatorId, rejectReason, SUCCESS,
                    "{\"status\":\"%s\"}".formatted(before.auditStatus()),
                    "{\"status\":\"%s\"}".formatted(after.auditStatus()));
            AdminAuditResultVO result = completedResult(log, auditResult, rejectReason, operatorId, after.reviewedAt());
            support.writeJson(idemKey, result, Duration.ofHours(24));
            return result;
        } finally {
            unlock(lockKey, lockValue);
        }
    }

    @Override
    @Transactional
    public AdminAuditResultVO auditRefund(Long refundId, AdminAuditRequest request) {
        return queuedAudit("REFUND_AUDIT", "payment-module", "REFUND", refundId, request);
    }

    @Override
    @Transactional
    public AdminAuditResultVO auditVerificationReversal(Long reversalId, AdminAuditRequest request) {
        return queuedAudit("VERIFICATION_REVERSAL_AUDIT", "verification-module", "VERIFICATION_REVERSAL", reversalId, request);
    }

    @Override
    @Transactional
    public AdminAuditResultVO triggerSettlement(Long settlementId, AdminAuditRequest request) {
        return queuedAudit("SETTLEMENT_TRIGGER", "payment-module", "SETTLEMENT", settlementId, request);
    }

    private AdminAuditResultVO queuedAudit(String actionType, String targetModule, String targetType,
                                           Long targetId, AdminAuditRequest request) {
        String auditResult = normalizeAuditResult(request.auditResult());
        String rejectReason = normalizeRejectReason(auditResult, request.rejectReason());
        Long operatorId = currentUserContext.requireUserId();
        String key = "admin:idem:audit:%s".formatted(request.requestId());
        AdminAuditResultVO repeated = repeatedResult(key, request.requestId());
        if (repeated != null) {
            return repeated;
        }
        String payload = "{\"targetId\":%d,\"auditResult\":\"%s\",\"rejectReason\":%s,\"operatorId\":%d}"
                .formatted(targetId, auditResult, jsonString(rejectReason), operatorId);
        support.compensation(actionType, String.valueOf(targetId), request.requestId(), targetModule, payload);
        AdminAuditLog log = support.audit(actionType, targetModule, targetType, String.valueOf(targetId),
                request.requestId(), operatorId, rejectReason, SUCCESS, null, payload);
        AdminAuditResultVO result = new AdminAuditResultVO(log.getId(), targetModule, targetType,
                String.valueOf(targetId), auditResult, SUCCESS, "PROCESSING", rejectReason, operatorId,
                "审核动作已记录，等待业务模块处理", log.getCreatedAt(), null);
        support.writeJson(key, result, Duration.ofHours(24));
        return result;
    }

    private AdminAuditResultVO repeatedResult(String idemKey, String requestId) {
        AdminAuditResultVO cached = support.readJson(idemKey, AdminAuditResultVO.class);
        if (cached != null) {
            return cached;
        }
        AdminAuditLog existed = auditLogMapper.findByRequestId(requestId);
        if (existed == null) {
            return null;
        }
        String result = extractAuditResult(existed);
        return new AdminAuditResultVO(existed.getId(), existed.getTargetModule(), existed.getTargetType(),
                existed.getTargetId(), result, existed.getOperationResult(), SUCCESS,
                existed.getOperationReason(), existed.getOperatorId(), "重复请求，返回已有审核结果",
                existed.getCreatedAt(), existed.getCreatedAt());
    }

    private AdminAuditResultVO completedResult(AdminAuditLog log, String auditResult, String rejectReason,
                                                Long operatorId, LocalDateTime reviewedAt) {
        return new AdminAuditResultVO(log.getId(), log.getTargetModule(), log.getTargetType(), log.getTargetId(),
                auditResult, SUCCESS, SUCCESS, rejectReason, operatorId, "审核完成", log.getCreatedAt(), reviewedAt);
    }

    private void notifyDrivingLicense(DrivingLicenseAuditDetailVO detail, String result,
                                      String reason, String requestId) {
        notificationService.createEvent(new CreateNotificationEventRequest(
                "APPROVED".equals(result) ? NotificationEventTypes.DRIVING_LICENSE_AUDIT_APPROVED
                        : NotificationEventTypes.DRIVING_LICENSE_AUDIT_REJECTED,
                "USER", detail.userId(), "CERTIFICATION",
                "DRIVING_LICENSE_CERTIFICATION", String.valueOf(detail.certificationId()),
                "驾驶证认证审核结果", notificationContent("驾驶证认证", result, reason),
                requestId + ":notify"));
    }

    private void notifyVehicle(VehicleCertificationAuditDetailVO detail, String result,
                               String reason, String requestId) {
        notificationService.createEvent(new CreateNotificationEventRequest(
                "APPROVED".equals(result) ? NotificationEventTypes.VEHICLE_CERT_AUDIT_APPROVED
                        : NotificationEventTypes.VEHICLE_CERT_AUDIT_REJECTED,
                "USER", detail.userId(), "CERTIFICATION",
                "VEHICLE_CERTIFICATION", String.valueOf(detail.certificationId()),
                "车辆认证审核结果", notificationContent("车辆认证", result, reason),
                requestId + ":notify"));
    }

    private String notificationContent(String name, String result, String reason) {
        return "APPROVED".equals(result)
                ? name + "已通过人工审核"
                : name + "未通过人工审核：" + reason;
    }

    private String normalizeAuditResult(String value) {
        String result = support.normalize(value);
        if (!List.of("APPROVED", "REJECTED").contains(result)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核结果仅支持 APPROVED 或 REJECTED");
        }
        return result;
    }

    private String normalizeRejectReason(String result, String reason) {
        String value = reason == null ? "" : reason.trim();
        if ("REJECTED".equals(result) && (value.length() < 2 || value.length() > 255)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驳回原因长度应为 2 到 255 个字符");
        }
        return "APPROVED".equals(result) ? null : value;
    }

    private void lock(String key, String value) {
        Boolean locked = redis.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(409, "该认证申请正在审核中");
        }
    }

    private void unlock(String key, String value) {
        try {
            if (value.equals(redis.opsForValue().get(key))) {
                redis.delete(key);
            }
        } catch (Exception ignored) {
            // 数据库条件更新仍提供最终并发保护。
        }
    }

    private String idemKey(String type, String requestId) {
        return "admin:idem:audit:%s:%s".formatted(type, requestId);
    }

    private String certificationSnapshot(String status, String reason) {
        return "{\"status\":\"%s\",\"rejectReason\":%s}".formatted(status, jsonString(reason));
    }

    private String extractAuditResult(AdminAuditLog log) {
        String snapshot = log.getAfterSnapshot();
        if (snapshot != null && snapshot.contains("\"APPROVED\"")) {
            return "APPROVED";
        }
        if (snapshot != null && snapshot.contains("\"REJECTED\"")) {
            return "REJECTED";
        }
        return "";
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
