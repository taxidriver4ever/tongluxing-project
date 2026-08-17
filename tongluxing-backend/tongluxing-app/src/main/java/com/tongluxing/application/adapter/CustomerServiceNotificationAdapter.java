package com.tongluxing.application.adapter;

import org.springframework.stereotype.Component;
import com.tongluxing.customerservice.integration.CustomerServiceNotificationPort;
import com.tongluxing.notify.dto.CreateNotificationEventRequest;
import com.tongluxing.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;

/** 把客服回复、关闭事件转换为幂等站内通知。 */
@Component
@RequiredArgsConstructor
public class CustomerServiceNotificationAdapter implements CustomerServiceNotificationPort {
    private final NotificationService notificationService;

    @Override
    public void notifyUser(Long userId, String eventType, Long ticketId,
                           String title, String content, String requestId) {
        notificationService.createEvent(new CreateNotificationEventRequest(
                eventType, "USER", userId, "CUSTOMER_SERVICE", "TICKET",
                String.valueOf(ticketId), title, content, requestId));
    }
}
