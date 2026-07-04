package com.tongluxing.customerservice.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 客服模块查询数据对象。
 */
@Data
public class CustomerServiceQueryDTO {

    private Long id;
    private String creatorType;
    private Long creatorId;
    private String scene;
    private String targetType;
    private String targetId;
    private String title;
    private String content;
    private String ticketStatus;
    private String priority;
    private Long assignedAdminId;
    private String senderType;
    private Long senderId;
    private String messageType;
    private String imageKeysJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
