package com.tongdao.admin.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.tongdao.admin.service.AdminOverviewService;
import com.tongdao.admin.vo.AdminOperationOverviewVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {
    private final AdminSupport support;

    @Override
    public AdminOperationOverviewVO overview(LocalDateTime startTime, LocalDateTime endTime) {
        String key = "admin:overview:%s:%s".formatted(startTime == null ? "all" : startTime,
                endTime == null ? "all" : endTime);
        AdminOperationOverviewVO cached = support.readJson(key, AdminOperationOverviewVO.class);
        if (cached != null) {
            return cached;
        }
        AdminOperationOverviewVO result = new AdminOperationOverviewVO(0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L, 0L, 0L);
        support.writeJson(key, result, Duration.ofMinutes(5));
        return result;
    }
}
