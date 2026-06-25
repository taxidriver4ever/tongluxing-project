package com.tongdao.admin.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongdao.admin.dto.AdminAuditRequest;
import com.tongdao.admin.entity.AdminAuditLog;
import com.tongdao.admin.mapper.AdminAuditLogMapper;
import com.tongdao.admin.service.AdminAuditService;
import com.tongdao.admin.vo.AdminAuditResultVO;
import com.tongdao.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {
    private final AdminAuditLogMapper auditLogMapper;
    private final AdminSupport support;

    @Override
    @Transactional
    public AdminAuditResultVO auditUserCertification(Long certificationId, AdminAuditRequest request) {
        return audit("USER_CERT_AUDIT", "user-module", "USER_CERTIFICATION", certificationId, request);
    }

    @Override
    @Transactional
    public AdminAuditResultVO auditMerchantApplication(Long applicationId, AdminAuditRequest request) {
        return audit("MERCHANT_AUDIT", "merchant-module", "MERCHANT_APPLICATION", applicationId, request);
    }

    @Override
    @Transactional
    public AdminAuditResultVO auditRefund(Long refundId, AdminAuditRequest request) {
        return audit("REFUND_AUDIT", "payment-module", "REFUND", refundId, request);
    }

    @Override
    @Transactional
    public AdminAuditResultVO auditVerificationReversal(Long reversalId, AdminAuditRequest request) {
        return audit("VERIFICATION_REVERSAL_AUDIT", "verification-module", "VERIFICATION_REVERSAL", reversalId, request);
    }

    @Override
    @Transactional
    public AdminAuditResultVO triggerSettlement(Long settlementId, AdminAuditRequest request) {
        return audit("SETTLEMENT_TRIGGER", "payment-module", "SETTLEMENT", settlementId, request);
    }

    private AdminAuditResultVO audit(String actionType, String targetModule, String targetType,
                                     Long targetId, AdminAuditRequest request) {
        validateAuditResult(request.auditResult());
        String idemKey = "admin:idem:audit:%s".formatted(request.requestId());
        AdminAuditResultVO cached = support.readJson(idemKey, AdminAuditResultVO.class);
        if (cached != null) {
            return cached;
        }
        AdminAuditLog existed = auditLogMapper.findByRequestId(request.requestId());
        if (existed != null) {
            AdminAuditResultVO result = toAuditResult(existed, extractAuditResult(existed), "重复请求，返回已有审核结果");
            support.writeJson(idemKey, result, Duration.ofHours(24));
            return result;
        }

        String auditResult = support.normalize(request.auditResult());
        String payload = """
                {"targetId":%d,"auditResult":"%s","rejectReason":%s}
                """.formatted(targetId, auditResult, jsonString(request.rejectReason())).trim();
        support.compensation(actionType, String.valueOf(targetId), request.requestId(), targetModule, payload);
        AdminAuditLog log = support.audit(actionType, targetModule, targetType, String.valueOf(targetId),
                request.requestId(), request.operatorId(), request.rejectReason(), AdminSupport.SUCCESS,
                null, payload);
        AdminAuditResultVO result = toAuditResult(log, auditResult, "审核动作已记录，业务模块处理通过补偿任务承接");
        support.writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    private void validateAuditResult(String auditResult) {
        String value = support.normalize(auditResult);
        if (!"APPROVED".equals(value) && !"REJECTED".equals(value)) {
            throw new BusinessException("审核结果仅支持 APPROVED 或 REJECTED");
        }
    }

    private AdminAuditResultVO toAuditResult(AdminAuditLog log, String auditResult, String message) {
        return new AdminAuditResultVO(log.getId(), log.getTargetModule(), log.getTargetType(), log.getTargetId(),
                auditResult, log.getOperationResult(), message, log.getCreatedAt());
    }

    private String extractAuditResult(AdminAuditLog log) {
        String snapshot = log.getAfterSnapshot();
        if (snapshot == null) {
            return "";
        }
        if (snapshot.contains("\"APPROVED\"")) {
            return "APPROVED";
        }
        if (snapshot.contains("\"REJECTED\"")) {
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
