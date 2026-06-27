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

/**
 * 后台人工干预服务实现。
 *
 * <p>当前仅支持拼团活动干预。干预动作会写审计日志并创建补偿任务，由 groupbuy-module 后续承接真实状态变更。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminInterventionServiceImpl implements AdminInterventionService {

    /** 审计日志 Mapper，用于幂等查询。 */
    private final AdminAuditLogMapper auditLogMapper;
    /** 后台通用支撑组件。 */
    private final AdminSupport support;

    /** 对拼团活动执行人工干预。 */
    @Override
    @Transactional
    public AdminInterventionResultVO interveneGroupbuy(Long activityId, AdminInterventionRequest request) {
        validateAction(request.action());
        String idemKey = "admin:idem:intervene:%s".formatted(request.requestId());
        AdminInterventionResultVO cached = support.readJson(idemKey, AdminInterventionResultVO.class);
        if (cached != null) {
            return cached;
        }
        // requestId 作为幂等键，防止运营端重复点击造成重复补偿任务。
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

    /** 校验拼团干预动作枚举。 */
    private void validateAction(String action) {
        String value = support.normalize(action);
        if (!"FORCE_SUCCESS".equals(value) && !"FORCE_FAILED".equals(value) && !"OFFLINE".equals(value)) {
            throw new BusinessException("拼团干预动作仅支持 FORCE_SUCCESS、FORCE_FAILED、OFFLINE");
        }
    }

    /** 将审计日志转换为干预结果响应。 */
    private AdminInterventionResultVO toResult(AdminAuditLog log, String action, String message) {
        return new AdminInterventionResultVO(log.getId(), log.getTargetModule(), log.getTargetType(), log.getTargetId(),
                support.normalize(action), log.getOperationResult(), message, log.getCreatedAt());
    }

    /** 转义 JSON 字符串中的特殊字符。 */
    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
