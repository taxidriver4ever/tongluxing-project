package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.admin.integration.AdminGroupbuyPort;
import com.tongluxing.groupbuy.service.GroupbuyService;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台补偿任务调用拼团模块的应用层适配器。
 */
@Component
@RequiredArgsConstructor
public class AdminGroupbuyAdapter implements AdminGroupbuyPort {

    private final GroupbuyService groupbuyService;

    @Override
    public void applyIntervention(Long activityId, String action, String reason, String requestId) {
        groupbuyService.applyAdminIntervention(activityId, action, reason, requestId);
    }
}
