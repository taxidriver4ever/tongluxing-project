package com.tongluxing.verification.job;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.verification.service.VerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核销补偿任务定时消费器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCompensationTaskJob {
    private static final String JOB_LOCK_KEY = "verification:job:compensation";

    private final VerificationService verificationService;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelayString = "${tongluxing.jobs.verification-compensation-delay:30000}")
    public void processDueTasks() {
        Boolean locked = redis.opsForValue().setIfAbsent(JOB_LOCK_KEY, "1", 25, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            int count = verificationService.processCompensationTasks(50);
            if (count > 0) {
                log.info("Processed {} verification compensation tasks", count);
            }
        } catch (Exception ex) {
            log.warn("Process verification compensation tasks failed", ex);
        } finally {
            redis.delete(JOB_LOCK_KEY);
        }
    }
}
