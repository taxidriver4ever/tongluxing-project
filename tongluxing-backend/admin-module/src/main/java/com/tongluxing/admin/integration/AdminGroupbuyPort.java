package com.tongluxing.admin.integration;

/**
 * 运营后台消费拼团干预任务的端口。
 */
public interface AdminGroupbuyPort {

    void applyIntervention(Long activityId, String action, String reason, String requestId);
}
