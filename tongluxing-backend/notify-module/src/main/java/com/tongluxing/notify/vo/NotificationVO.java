package com.tongluxing.notify.vo;

import java.time.LocalDateTime;

/**
 * 通知前端展示对象。
 */
public record NotificationVO(
        Long id,
        String receiverType,
        Long receiverId,
        String scene,
        String eventType,
        String title,
        String content,
        String targetType,
        String targetId,
        String readStatus,
        LocalDateTime readAt,
        LocalDateTime createdAt) {
}
