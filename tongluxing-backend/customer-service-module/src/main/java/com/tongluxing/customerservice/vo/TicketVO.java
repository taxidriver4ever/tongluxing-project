package com.tongluxing.customerservice.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服工单展示对象。
 *
 * @param id 工单 ID
 * @param creatorType 创建者类型，例如 USER
 * @param creatorId 创建者 ID
 * @param scene 工单场景
 * @param targetType 关联对象类型
 * @param targetId 关联对象 ID
 * @param title 工单标题
 * @param content 工单摘要内容
 * @param ticketStatus 工单状态：OPEN、PROCESSING、CLOSED
 * @param priority 工单优先级
 * @param assignedAdminId 当前处理人 ID
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间
 * @param closedAt 关闭时间，未关闭时为空
 * @param messages 工单消息时间线
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
