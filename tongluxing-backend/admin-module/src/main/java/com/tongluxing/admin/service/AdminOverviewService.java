package com.tongluxing.admin.service;

import java.time.LocalDateTime;

import com.tongluxing.admin.vo.AdminOperationOverviewVO;

/**
 * 运营概览服务。
 */
public interface AdminOverviewService {

    /** 查询指定时间范围的运营概览指标。 */
    AdminOperationOverviewVO overview(LocalDateTime startTime, LocalDateTime endTime);
}
