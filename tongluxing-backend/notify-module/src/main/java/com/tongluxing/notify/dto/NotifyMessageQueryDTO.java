package com.tongluxing.notify.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 通知查询数据对象。
 */
@Data
public class NotifyMessageQueryDTO {

    private Long id;
    private String receiverType;
    private Long receiverId;
    private String scene;
    private String eventType;
    private String title;
    private String content;
    private String targetType;
    private String targetId;
    private String readStatus;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
