package com.tongluxing.order.job;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 待支付订单超时关闭任务。
 *
 * <p>周期性扫描已过期且未支付的订单，并在关闭后写入必要的跨模块补偿任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCloseJob {
    private static final String JOB_LOCK_KEY = "order:job:timeout-close";

    private final OrderService orderService;
    private final StringRedisTemplate redis;

    /**
     * 定时关闭过期待支付订单。
     */
    @Scheduled(fixedDelayString = "${tongluxing.jobs.order-timeout-close-delay:60000}")
    public void closeExpiredOrders() {
        Boolean locked = redis.opsForValue().setIfAbsent(JOB_LOCK_KEY, "1", 55, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int count = orderService.closeExpiredWaitPay(100);
            if (count > 0) {
                log.info("Closed {} expired wait-pay orders", count);
            }
        } catch (Exception ex) {
            log.warn("Close expired wait-pay orders failed", ex);
        } finally {
            redis.delete(JOB_LOCK_KEY);
        }
    }
}
