package com.tongluxing.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tongluxing.admin.dto.AdminAuditLogQueryRequest;
import com.tongluxing.admin.entity.AdminAuditLog;
import com.tongluxing.admin.mapper.AdminAuditLogMapper;
import com.tongluxing.admin.service.AdminQueryService;
import com.tongluxing.admin.vo.AdminAuditLogVO;
import com.tongluxing.admin.vo.PageResult;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminQueryServiceImpl implements AdminQueryService {

    /** 审计日志 Mapper。 */
    private final AdminAuditLogMapper auditLogMapper;

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
