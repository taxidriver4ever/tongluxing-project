package com.tongluxing.assessment.vo;

/**
 * 月度考核运行结果。
 *
 * @param period 本次执行的考核周期
 * @param merchantCount 本次扫描到的有效商家数量
 * @param successCount 成功完成考核计算的商家数量
 */
public record MonthlyRunResultVO(String period, int merchantCount, int successCount) {
}
