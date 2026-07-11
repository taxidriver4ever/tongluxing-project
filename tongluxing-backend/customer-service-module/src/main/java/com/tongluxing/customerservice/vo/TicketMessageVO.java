package com.tongluxing.customerservice.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服工单消息展示对象。
 *
 * @param id 消息 ID
 * @param ticketId 所属工单 ID
 * @param senderType 发送方类型：USER、ADMIN 或 SYSTEM
 * @param senderId 发送方 ID；系统消息固定为 0
 * @param messageType 消息类型，例如 TEXT
 * @param content 消息正文
 * @param imageKeys 图片附件对象存储 Key 列表
 * @param createdAt 消息创建时间
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
