package com.tongluxing.order.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 订单跨模块补偿任务实体，对应 order_compensation_task 表。
 *
 * <p>用于异步推进优惠券锁定、确认、释放等副作用，降低订单模块与外部模块之间的事务耦合。</p>
 */
@Data
public class OrderCompensationTask {
    /** 任务主键。 */
    private Long id;
    /** 业务类型，如 COUPON_LOCK、COUPON_CONFIRM、COUPON_RELEASE。 */
    private String bizType;
    /** 业务 ID，通常为订单 ID。 */
    private String bizId;
    /** 幂等键，供目标模块或任务消费侧识别重复请求。 */
    private String idempotentKey;
    /** 目标模块名称。 */
    private String targetModule;
    /** 请求载荷 JSON。 */
    private String requestPayload;
    /** 任务状态：PENDING、FAILED、SUCCESS。 */
    private String taskStatus;
    /** 已重试次数。 */
    private Integer retryCount;
    /** 下次可重试时间。 */
    private LocalDateTime nextRetryAt;
    /** 最近一次失败原因。 */
    private String lastError;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

