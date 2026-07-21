package com.tongluxing.customerservice.integration;

/** 客服模块向通知模块发送用户可见事件的端口。 */
public interface CustomerServiceNotificationPort {
    void notifyUser(Long userId, String eventType, Long ticketId,
                    String title, String content, String requestId);
}
