package com.tongdao.admin.service;

import java.time.LocalDateTime;

import com.tongdao.admin.dto.AdminAuditLogQueryRequest;
import com.tongdao.admin.vo.AdminAuditLogVO;
import com.tongdao.admin.vo.PageResult;

public interface AdminQueryService {

    PageResult<AdminAuditLogVO> auditLogs(AdminAuditLogQueryRequest request);

    PageResult<Object> emptyBusinessPage(String module, String status, String keyword,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size);
}
