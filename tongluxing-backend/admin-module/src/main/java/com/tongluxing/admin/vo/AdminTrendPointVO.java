package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 运营总览每日趋势点。 */
public record AdminTrendPointVO(
        LocalDate date, Long newUsers, Long orders, Long verifications, BigDecimal gmv) {}
