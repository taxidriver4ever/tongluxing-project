package com.tongluxing.admin.job;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.admin.service.AdminCompensationTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 运营后台补偿任务定时消费器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminCompensationTaskJob {
    private static final String JOB_LOCK_KEY = "admin:compensation:job";

    private final AdminCompensationTaskService taskService;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelayString = "${tongluxing.jobs.admin-compensation-delay:60000}")
    public void processDueTasks() {
        Boolean locked = redis.opsForValue().setIfAbsent(JOB_LOCK_KEY, "1", 55, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int count = taskService.processDueTasks(50);
            if (count > 0) {
                log.info("Processed {} admin compensation tasks", count);
            }
        } catch (Exception ex) {
            log.warn("Process admin compensation tasks failed", ex);
        } finally {
            redis.delete(JOB_LOCK_KEY);
        }
    }
}
