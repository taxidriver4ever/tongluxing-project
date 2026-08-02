package com.tongluxing.user.vo;

import java.math.BigDecimal;

/**
 * 组队匹配结果返回对象。
 *
 * <p>routeOverlapRate 表示路线重合比例，timeDifferenceMinutes 表示出发时间差，
 * 调用方可据此解释推荐排序。</p>
 *
 * @param teamId 组队 ID
 * @param teamName 组队名称
 * @param routeOverlapRate 路线重合比例
 * @param timeDifferenceMinutes 出发时间差（分钟）
 */
public record TeamMatchVO(Long teamId, String teamName, BigDecimal routeOverlapRate, Long timeDifferenceMinutes) {
}

