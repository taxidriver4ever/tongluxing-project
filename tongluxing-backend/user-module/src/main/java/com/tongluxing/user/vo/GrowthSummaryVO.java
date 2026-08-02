package com.tongluxing.user.vo;

/**
 * 成长值概览返回对象。
 *
 * @param totalPoints 当前累计成长值
 * @param levelCode 当前等级编码
 * @param nextLevelPoints 距离下一等级所需的目标成长值
 */
public record GrowthSummaryVO(Integer totalPoints, String levelCode, Integer nextLevelPoints) {
}

