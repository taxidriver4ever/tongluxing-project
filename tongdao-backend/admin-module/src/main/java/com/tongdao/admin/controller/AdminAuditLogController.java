package com.tongdao.admin.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.admin.dto.AdminAuditLogQueryRequest;
import com.tongdao.admin.service.AdminQueryService;
import com.tongdao.admin.vo.AdminAuditLogVO;
import com.tongdao.admin.vo.PageResult;
import com.tongdao.common.result.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/audit-logs")
public class AdminAuditLogController {
    private final AdminQueryService queryService;

    @GetMapping
    public Result<PageResult<AdminAuditLogVO>> page(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetModule,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminAuditLogQueryRequest request = new AdminAuditLogQueryRequest(operatorId, actionType, targetModule,
                targetType, targetId, startTime, endTime, page, size);
        return Result.success(queryService.auditLogs(request));
    }
}
