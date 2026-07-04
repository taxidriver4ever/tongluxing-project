package com.tongluxing.customerservice.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服工单展示对象。
 */
public record TicketVO(
        Long id,
        String creatorType,
        Long creatorId,
        String scene,
        String targetType,
        String targetId,
        String title,
        String content,
        String ticketStatus,
        String priority,
        Long assignedAdminId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt,
        List<TicketMessageVO> messages) {
}
