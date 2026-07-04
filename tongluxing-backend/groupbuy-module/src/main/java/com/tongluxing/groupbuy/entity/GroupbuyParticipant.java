package com.tongluxing.groupbuy.entity;

import java.time.LocalDateTime;

import lombok.Data;
/**
 * GroupbuyParticipant 数据库实体。
 */

@Data
public class GroupbuyParticipant {
    private Long id;
    private Long activityId;
    private Long orderId;
    private Long userId;
    private String participantStatus;
    private LocalDateTime joinedAt;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

