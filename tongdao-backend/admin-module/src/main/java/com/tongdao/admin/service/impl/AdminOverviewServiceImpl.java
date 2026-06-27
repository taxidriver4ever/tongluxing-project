package com.tongdao.admin.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.tongdao.admin.service.AdminOverviewService;
import com.tongdao.admin.vo.AdminOperationOverviewVO;

import lombok.RequiredArgsConstructor;

/**
 * 运营概览服务实现。
 *
 * <p>当前返回基础零值概览并做短期缓存，后续可按模块接入真实统计口径。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

    /** 后台通用支撑组件。 */
    private final AdminSupport support;

    /** 查询运营概览；相同时间范围结果缓存 5 分钟。 */
    @Override
    public AdminOperationOverviewVO overview(LocalDateTime startTime, LocalDateTime endTime) {
        String key = "admin:overview:%s:%s".formatted(startTime == null ? "all" : startTime,
                endTime == null ? "all" : endTime);
        AdminOperationOverviewVO cached = support.readJson(key, AdminOperationOverviewVO.class);
        if (cached != null) {
            return cached;
        }
        // 目前统计口径尚未接入各业务模块，先返回结构完整的零值结果，保证接口可用。
        AdminOperationOverviewVO result = new AdminOperationOverviewVO(0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L, 0L, 0L);
        support.writeJson(key, result, Duration.ofMinutes(5));
        return result;
    }
}
