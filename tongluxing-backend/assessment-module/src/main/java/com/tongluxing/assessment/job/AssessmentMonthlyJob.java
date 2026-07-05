package com.tongluxing.assessment.job;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.assessment.dto.MonthlyAssessmentRunRequest;
import com.tongluxing.assessment.service.AssessmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 商家月度考核自动任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentMonthlyJob {
    private static final Long SYSTEM_OPERATOR_ID = 0L;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AssessmentService assessmentService;
    private final StringRedisTemplate redis;

    /**
     * 每月 1 日凌晨 3 点计算上一个自然月考核。
     */
    @Scheduled(cron = "${tongluxing.jobs.assessment-monthly-cron:0 0 3 1 * ?}")
    public void runMonthly() {
        String period = LocalDate.now().minusMonths(1).format(PERIOD_FORMATTER);
        String lockKey = "assessment:job:monthly:%s".formatted(period);
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", 2, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            String requestId = "assessment-monthly-job-" + period;
            assessmentService.runMonthly(new MonthlyAssessmentRunRequest(period, SYSTEM_OPERATOR_ID, requestId));
            log.info("Monthly assessment job finished, period={}", period);
        } catch (Exception ex) {
            log.warn("Monthly assessment job failed, period={}", period, ex);
        }
    }
}
