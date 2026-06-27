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

/**
 * 后台审核服务实现。
 *
 * <p>审核动作本模块先完成参数校验、幂等控制、审计日志记录和补偿任务创建；
 * 具体业务状态变更由对应业务模块通过补偿任务承接，避免 admin-module 直接写其他模块表。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    /** 审计日志 Mapper，用于幂等查询和日志写入。 */
    private final AdminAuditLogMapper auditLogMapper;
    /** 后台通用支撑组件。 */
    private final AdminSupport support;

    /** 审核用户认证申请。 */
    @Override
    @Transactional
    public AdminAuditResultVO auditUserCertification(Long certificationId, AdminAuditRequest request) {
        return audit("USER_CERT_AUDIT", "user-module", "USER_CERTIFICATION", certificationId, request);
    }

    /** 审核商家入驻申请。 */
    @Override
    @Transactional
    public AdminAuditResultVO auditMerchantApplication(Long applicationId, AdminAuditRequest request) {
        return audit("MERCHANT_AUDIT", "merchant-module", "MERCHANT_APPLICATION", applicationId, request);
    }

    /** 审核退款申请。 */
    @Override
    @Transactional
    public AdminAuditResultVO auditRefund(Long refundId, AdminAuditRequest request) {
        return audit("REFUND_AUDIT", "payment-module", "REFUND", refundId, request);
    }

    /** 审核核销撤销申请。 */
    @Override
    @Transactional
    public AdminAuditResultVO auditVerificationReversal(Long reversalId, AdminAuditRequest request) {
        return audit("VERIFICATION_REVERSAL_AUDIT", "verification-module", "VERIFICATION_REVERSAL", reversalId, request);
    }

    /** 触发结算处理。 */
    @Override
    @Transactional
    public AdminAuditResultVO triggerSettlement(Long settlementId, AdminAuditRequest request) {
        return audit("SETTLEMENT_TRIGGER", "payment-module", "SETTLEMENT", settlementId, request);
    }

    /** 通用审核模板：校验审核结果、处理幂等、创建补偿任务并写审计日志。 */
    private AdminAuditResultVO audit(String actionType, String targetModule, String targetType,
                                     Long targetId, AdminAuditRequest request) {
        validateAuditResult(request.auditResult());
        String idemKey = "admin:idem:audit:%s".formatted(request.requestId());
        AdminAuditResultVO cached = support.readJson(idemKey, AdminAuditResultVO.class);
        if (cached != null) {
            return cached;
        }
        // requestId 是业务幂等键；即使 Redis 缓存失效，也要以 MySQL 审计日志作为最终幂等依据。
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

    /** 校验审核结果枚举。 */
    private void validateAuditResult(String auditResult) {
        String value = support.normalize(auditResult);
        if (!"APPROVED".equals(value) && !"REJECTED".equals(value)) {
            throw new BusinessException("审核结果仅支持 APPROVED 或 REJECTED");
        }
    }

    /** 将审计日志转换为审核结果响应。 */
    private AdminAuditResultVO toAuditResult(AdminAuditLog log, String auditResult, String message) {
        return new AdminAuditResultVO(log.getId(), log.getTargetModule(), log.getTargetType(), log.getTargetId(),
                auditResult, log.getOperationResult(), message, log.getCreatedAt());
    }

    /** 从历史审计快照中提取审核结果，用于幂等重复请求返回。 */
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

    /** 将字符串转换为 JSON 字符串字面量；空值返回 null。 */
    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
