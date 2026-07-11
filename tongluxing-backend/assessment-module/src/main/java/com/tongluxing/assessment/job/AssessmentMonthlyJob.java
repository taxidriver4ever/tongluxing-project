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
 *
 * <p>定时任务只负责确定考核周期和抢占任务锁，实际计算仍委托 {@link AssessmentService}。
 * 这样人工触发、内部补偿和自动调度可以复用同一套计算逻辑。</p>
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
     *
     * <p>Redis 锁用于多实例部署时的互斥，锁定失败直接跳过，避免重复写入同一周期数据。</p>
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
            // 自动任务使用稳定 requestId，重复触发时由 Service 层幂等逻辑兜底。
            String requestId = "assessment-monthly-job-" + period;
            assessmentService.runMonthly(new MonthlyAssessmentRunRequest(period, SYSTEM_OPERATOR_ID, requestId));
            log.info("Monthly assessment job finished, period={}", period);
        } catch (Exception ex) {
            log.warn("Monthly assessment job failed, period={}", period, ex);
        }
    }
}
