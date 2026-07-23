package com.tongluxing.admin.service;

import java.time.YearMonth;

/** 按成长等级执行每月优惠券权益发放。 */
public interface MonthlyLevelCouponGrantService {

    GrantSummary grant(YearMonth month);

    /** 一次发券任务的统计结果。 */
    record GrantSummary(String month, int eligibleUsers, int issued, int duplicates, int failed, boolean enabled) {
    }
}
