package com.tongluxing.admin.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.tongluxing.admin.service.AdminOverviewService;
import com.tongluxing.admin.vo.AdminOperationOverviewVO;
import com.tongluxing.admin.mapper.AdminTradeMapper;

import lombok.RequiredArgsConstructor;

/**
 * 运营概览服务实现。
 *
 * <p>当前返回基础零值概览并做短期缓存，后续可按模块接入真实统计口径。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

    private final AdminTradeMapper mapper;

    /** 查询运营概览；相同时间范围结果缓存 5 分钟。 */
    @Override
    public AdminOperationOverviewVO overview(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime == null ? LocalDate.now().atStartOfDay() : startTime;
        LocalDateTime end = endTime == null ? start.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1) : endTime;
        return new AdminOperationOverviewVO(mapper.registeredUsers(), mapper.activeUsers(), mapper.newUsers(start,end),
                mapper.certifiedVehicles(), mapper.merchants(), mapper.pendingMerchants(), mapper.activeMerchants(),
                mapper.groupbuys(), mapper.orders(), mapper.ordersBetween(start,end), mapper.paidAmount(), mapper.paidBetween(start,end),
                mapper.verifications(), mapper.commission(), mapper.couponOffers(), mapper.pendingRefunds(), mapper.pendingSettlements(), mapper.trends());
    }
}
