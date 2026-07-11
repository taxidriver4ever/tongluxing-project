package com.tongluxing.order.job;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.order.service.OrderCompensationTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单补偿任务定时消费器。
 *
 * <p>通过 Redis 分布式锁避免多实例同时拉取同一批补偿任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensationTaskJob {
    private static final String JOB_LOCK_KEY = "order:job:compensation";

    private final OrderCompensationTaskService taskService;
    private final StringRedisTemplate redis;

    /**
     * 定时消费到期补偿任务。
     */
    @Scheduled(fixedDelayString = "${tongluxing.jobs.order-compensation-delay:30000}")
    public void processDueTasks() {
        Boolean locked = redis.opsForValue().setIfAbsent(JOB_LOCK_KEY, "1", 25, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int count = taskService.processDueTasks(50);
            if (count > 0) {
                log.info("Processed {} order compensation tasks", count);
            }
        } catch (Exception ex) {
            log.warn("Process order compensation tasks failed", ex);
        } finally {
            redis.delete(JOB_LOCK_KEY);
        }
    }
}
