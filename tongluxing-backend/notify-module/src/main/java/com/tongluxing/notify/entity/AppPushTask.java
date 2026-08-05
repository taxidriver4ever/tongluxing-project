package com.tongluxing.notify.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 待投递系统推送任务。 */
@Data
public class AppPushTask {
    private Long id;
    private Long userId;
    private String eventType;
    private String title;
    private String content;
    private String payloadJson;
    private String idempotencyKey;
    private String deliveryStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
