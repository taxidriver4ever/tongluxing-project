package com.tongluxing.customerservice.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 客服模块查询数据对象。
 *
 * <p>该 DTO 同时承载工单表和消息表的查询结果。部分字段只在工单查询或消息查询中有值，
 * Service 层会按场景转换为 TicketVO 或 TicketMessageVO。</p>
 */
@Data
public class CustomerServiceQueryDTO {

    /** 工单或消息主键。 */
    private Long id;

    /** 创建者类型，例如 USER。 */
    private String creatorType;

    /** 创建者 ID。 */
    private Long creatorId;

    /** 工单场景，例如 REFUND、COMPLAINT。 */
    private String scene;

    /** 关联对象类型，例如 ORDER、VERIFICATION。 */
    private String targetType;

    /** 关联对象 ID；消息查询中复用为 ticketId 字符串。 */
    private String targetId;

    /** 工单标题。 */
    private String title;

    /** 工单摘要或消息正文。 */
    private String content;

    /** 工单状态，例如 OPEN、PROCESSING、CLOSED。 */
    private String ticketStatus;

    /** 工单优先级。 */
    private String priority;

    /** 当前处理该工单的运营人员 ID。 */
    private Long assignedAdminId;

    /** 消息发送方类型，例如 USER、ADMIN、SYSTEM。 */
    private String senderType;

    /** 消息发送方 ID。 */
    private Long senderId;

    /** 消息类型，例如 TEXT。 */
    private String messageType;

    /** 图片附件 Key 的 JSON 字符串。 */
    private String imageKeysJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 工单关闭时间。 */
    private LocalDateTime closedAt;
}
