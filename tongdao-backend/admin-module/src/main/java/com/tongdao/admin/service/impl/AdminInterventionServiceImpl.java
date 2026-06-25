package com.tongdao.admin.service.impl;

import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongdao.admin.dto.AdminInterventionRequest;
import com.tongdao.admin.entity.AdminAuditLog;
import com.tongdao.admin.mapper.AdminAuditLogMapper;
import com.tongdao.admin.service.AdminInterventionService;
import com.tongdao.admin.vo.AdminInterventionResultVO;
import com.tongdao.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminInterventionServiceImpl implements AdminInterventionService {
    private final AdminAuditLogMapper auditLogMapper;
    private final AdminSupport support;

    @Override
    @Transactional
    public AdminInterventionResultVO interveneGroupbuy(Long activityId, AdminInterventionRequest request) {
        validateAction(request.action());
        String idemKey = "admin:idem:intervene:%s".formatted(request.requestId());
        AdminInterventionResultVO cached = support.readJson(idemKey, AdminInterventionResultVO.class);
        if (cached != null) {
            return cached;
        }
        AdminAuditLog existed = auditLogMapper.findByRequestId(request.requestId());
        if (existed != null) {
            AdminInterventionResultVO result = toResult(existed, request.action(), "重复请求，返回已有干预结果");
            support.writeJson(idemKey, result, Duration.ofHours(24));
            return result;
        }

        String payload = """
                {"activityId":%d,"action":"%s","reason":"%s"}
                """.formatted(activityId, support.normalize(request.action()), escape(request.reason())).trim();
        support.compensation("GROUPBUY_INTERVENE", String.valueOf(activityId), request.requestId(),
                "groupbuy-module", payload);
        AdminAuditLog log = support.audit("GROUPBUY_INTERVENE", "groupbuy-module", "GROUPBUY_ACTIVITY",
                String.valueOf(activityId), request.requestId(), request.operatorId(), request.reason(),
                AdminSupport.SUCCESS, null, payload);
        AdminInterventionResultVO result = toResult(log, request.action(), "干预动作已记录，业务模块处理通过补偿任务承接");
        support.writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    private void validateAction(String action) {
        String value = support.normalize(action);
        if (!"FORCE_SUCCESS".equals(value) && !"FORCE_FAILED".equals(value) && !"OFFLINE".equals(value)) {
            throw new BusinessException("拼团干预动作仅支持 FORCE_SUCCESS、FORCE_FAILED、OFFLINE");
        }
    }

    private AdminInterventionResultVO toResult(AdminAuditLog log, String action, String message) {
        return new AdminInterventionResultVO(log.getId(), log.getTargetModule(), log.getTargetType(), log.getTargetId(),
                support.normalize(action), log.getOperationResult(), message, log.getCreatedAt());
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
