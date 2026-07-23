package com.tongluxing.admin.job;

import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.admin.service.MonthlyLevelCouponGrantService;

import lombok.RequiredArgsConstructor;

/** 每月 1 日按成长等级自动发放配置好的合作商优惠券。 */
@Component
@RequiredArgsConstructor
public class MonthlyLevelCouponGrantJob {
    private static final Logger log = LoggerFactory.getLogger(MonthlyLevelCouponGrantJob.class);
    private final MonthlyLevelCouponGrantService grantService;

    @Scheduled(cron = "${tongluxing.jobs.monthly-level-coupon-cron:0 15 3 1 * ?}")
    public void execute() {
        try {
            MonthlyLevelCouponGrantService.GrantSummary result = grantService.grant(YearMonth.now());
            log.info("Monthly level coupons finished: {}", result);
        } catch (RuntimeException exception) {
            // 未配置或配置错误时不阻断应用内其他定时任务。
            log.warn("Monthly level coupons skipped: {}", exception.getMessage());
        }
    }
}
