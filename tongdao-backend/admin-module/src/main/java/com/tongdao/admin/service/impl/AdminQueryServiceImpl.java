package com.tongdao.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tongdao.admin.dto.AdminAuditLogQueryRequest;
import com.tongdao.admin.entity.AdminAuditLog;
import com.tongdao.admin.mapper.AdminAuditLogMapper;
import com.tongdao.admin.service.AdminQueryService;
import com.tongdao.admin.vo.AdminAuditLogVO;
import com.tongdao.admin.vo.PageResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminQueryServiceImpl implements AdminQueryService {
    private final AdminAuditLogMapper auditLogMapper;

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

    @Override
    public PageResult<Object> emptyBusinessPage(String module, String status, String keyword,
                                                LocalDateTime startTime, LocalDateTime endTime,
                                                int page, int size) {
        return new PageResult<>(List.of(), 0L, normalizePage(page), normalizeSize(size));
    }

    private AdminAuditLogVO toVO(AdminAuditLog log) {
        return new AdminAuditLogVO(log.getId(), log.getOperatorId(), log.getOperatorName(), log.getActionType(),
                log.getTargetModule(), log.getTargetType(), log.getTargetId(), log.getRequestId(),
                log.getOperationReason(), log.getOperationResult(), log.getCreatedAt());
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
