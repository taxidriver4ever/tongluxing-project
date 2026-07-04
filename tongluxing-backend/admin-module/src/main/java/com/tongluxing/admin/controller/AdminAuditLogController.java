package com.tongluxing.admin.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminAuditLogQueryRequest;
import com.tongluxing.admin.service.AdminQueryService;
import com.tongluxing.admin.vo.AdminAuditLogVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台审计日志查询接口。
 *
 * <p>用于按操作人、动作类型、目标模块和时间范围分页查询后台操作记录。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/audit-logs")
public class AdminAuditLogController {

    /** 后台查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询后台审计日志。 */
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
