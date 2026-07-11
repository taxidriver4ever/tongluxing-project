package com.tongluxing.assessment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家考核结果。
 *
 * @param merchantId 商家 ID
 * @param period 考核周期，首次未计算时可能为空
 * @param score 当前展示分数
 * @param level 当前商家等级
 * @param commissionRate 当前等级佣金率
 * @param rankWeight 当前等级推荐排序权重
 * @param exclusionRadiusKm 当前等级商家排他半径，单位公里
 * @param nextLevel 下一等级，已达到最高等级时为空
 * @param needScore 距离下一等级还需要的分数
 * @param calculatedAt 最近一次考核计算时间
 */
public record MerchantAssessmentResultVO(
        Long merchantId,
        String period,
        BigDecimal score,
        String level,
        BigDecimal commissionRate,
        BigDecimal rankWeight,
        BigDecimal exclusionRadiusKm,
        String nextLevel,
        BigDecimal needScore,
        LocalDateTime calculatedAt) {
}
