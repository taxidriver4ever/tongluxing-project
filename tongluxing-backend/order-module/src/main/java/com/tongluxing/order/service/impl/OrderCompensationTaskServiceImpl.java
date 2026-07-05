package com.tongluxing.order.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.order.entity.OrderCompensationTask;
import com.tongluxing.order.entity.OrderTrade;
import com.tongluxing.order.integration.OrderCouponPort;
import com.tongluxing.order.mapper.OrderCompensationTaskMapper;
import com.tongluxing.order.mapper.OrderTradeMapper;
import com.tongluxing.order.service.OrderCompensationTaskService;

import lombok.RequiredArgsConstructor;

/**
 * 订单补偿任务消费实现。
 *
 * <p>补偿任务用于推进订单创建、取消、支付成功之后的跨模块副作用。
 * 任务本身以 MySQL 为事实源，Redis 只用于单任务互斥锁，避免多实例重复消费。
 * 批处理方法不包裹大事务，避免单个跨模块调用失败后把整批任务标记为 rollback-only。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderCompensationTaskServiceImpl implements OrderCompensationTaskService {
    private static final int MAX_LIMIT = 100;
    private static final String TASK_LOCK_KEY = "order:compensation:lock:%d";

    private final OrderCompensationTaskMapper taskMapper;
    private final OrderTradeMapper orderMapper;
    private final OrderCouponPort couponPort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public int processDueTasks(int limit) {
        LocalDateTime now = LocalDateTime.now();
        int processed = 0;
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        for (OrderCompensationTask task : taskMapper.listDueTasks(now, normalizedLimit)) {
            if (!tryLock(task.getId())) {
                continue;
            }
            try {
                processOne(task);
                taskMapper.markSuccess(task.getId(), now);
                processed++;
            } catch (Exception ex) {
                taskMapper.markFailed(task.getId(), normalizeError(ex), nextRetryAt(task, now), now);
            } finally {
                redis.delete(TASK_LOCK_KEY.formatted(task.getId()));
            }
        }
        return processed;
    }

    /**
     * 根据任务类型路由到对应本地模块，保持每种任务的职责清晰。
     */
    private void processOne(OrderCompensationTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getRequestPayload() == null ? "{}" : task.getRequestPayload());
        Long orderId = payload.path("orderId").asLong(Long.parseLong(task.getBizId()));
        if ("COUPON_LOCK".equals(task.getBizType())) {
            Long userCouponId = payload.path("userCouponId").asLong(0L);
            if (userCouponId == 0L) {
                throw new BusinessException("订单锁券任务缺少用户券 ID");
            }
            OrderTrade order = requireOrder(orderId);
            BigDecimal amountBeforeCoupon = order.getPayableAmount().add(order.getCouponDeductionAmount());
            couponPort.lockCoupon(userCouponId, orderId, amountBeforeCoupon);
        } else if ("COUPON_CONFIRM".equals(task.getBizType())) {
            couponPort.confirmCoupon(orderId);
        } else if ("COUPON_RELEASE".equals(task.getBizType())) {
            couponPort.releaseCoupon(orderId);
        } else {
            throw new BusinessException("不支持的订单补偿任务类型：" + task.getBizType());
        }
    }

    private OrderTrade requireOrder(Long orderId) {
        OrderTrade order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException("订单补偿任务关联订单不存在");
        }
        return order;
    }

    private boolean tryLock(Long taskId) {
        String key = TASK_LOCK_KEY.formatted(taskId);
        Boolean locked = redis.opsForValue().setIfAbsent(key, "1", 120, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    private LocalDateTime nextRetryAt(OrderCompensationTask task, LocalDateTime now) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        long delaySeconds = Math.min(300L, (long) Math.pow(2, Math.min(retryCount, 8)) * 10L);
        return now.plus(Duration.ofSeconds(delaySeconds));
    }

    private String normalizeError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
