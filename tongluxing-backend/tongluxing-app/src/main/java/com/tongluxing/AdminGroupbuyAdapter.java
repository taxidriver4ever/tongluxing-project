package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.admin.integration.AdminGroupbuyPort;
import com.tongluxing.groupbuy.service.GroupbuyService;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台补偿任务调用拼团模块的应用层适配器。
 *
 * <p>实现 admin-module 定义的端口，将后台干预动作转发给 groupbuy-module。</p>
 */
@Component
@RequiredArgsConstructor
public class AdminGroupbuyAdapter implements AdminGroupbuyPort {

    private final GroupbuyService groupbuyService;

    /**
     * 应用运营后台拼团干预动作。
     *
     * @param activityId 拼团活动 ID
     * @param action 干预动作，如 FORCE_SUCCESS、FORCE_FAILED、OFFLINE
     * @param reason 干预原因
     * @param requestId 幂等请求号
     */
    @Override
    public void applyIntervention(Long activityId, String action, String reason, String requestId, Integer extendMinutes) {
        groupbuyService.applyAdminIntervention(activityId, action, reason, requestId, extendMinutes);
    }
}
