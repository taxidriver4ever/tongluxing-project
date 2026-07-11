package com.tongluxing.groupbuy.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 拼团参与人数据库实体，对应 groupbuy_participant 表。
 *
 * <p>一条记录代表一个用户在某个拼团活动中的支付参团关系。</p>
 */
@Data
public class GroupbuyParticipant {
    /** 参与记录主键。 */
    private Long id;
    /** 所属拼团活动 ID。 */
    private Long activityId;
    /** 关联的订单 ID。 */
    private Long orderId;
    /** 参团用户 ID。 */
    private Long userId;
    /** 参与状态，如 PAID、REFUNDED。 */
    private String participantStatus;
    /** 加入拼团时间。 */
    private LocalDateTime joinedAt;
    /** 支付完成时间。 */
    private LocalDateTime paidAt;
    /** 退款完成时间。 */
    private LocalDateTime refundedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示有效。 */
    private Integer deleted;
}

