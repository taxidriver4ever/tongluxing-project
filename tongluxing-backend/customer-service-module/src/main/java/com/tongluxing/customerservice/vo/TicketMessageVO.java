package com.tongluxing.customerservice.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服工单消息展示对象。
 */
public record TicketMessageVO(
        Long id,
        Long ticketId,
        String senderType,
        Long senderId,
        String messageType,
        String content,
        List<String> imageKeys,
        LocalDateTime createdAt) {
}
