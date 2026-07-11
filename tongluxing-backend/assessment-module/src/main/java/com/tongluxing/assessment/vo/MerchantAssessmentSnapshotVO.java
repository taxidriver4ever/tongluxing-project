package com.tongluxing.assessment.vo;

import java.math.BigDecimal;

/**
 * 商家等级快照，供支付分账和推荐排序读取。
 *
 * <p>该 VO 是跨模块读取的轻量模型，只暴露交易和推荐链路需要的等级配置，
 * 不包含人工调整明细、历史分数等运营侧信息。</p>
 *
 * @param merchantId 商家 ID
 * @param level 商家等级
 * @param score 当前快照分
 * @param commissionRate 分账或结算使用的平台佣金率
 * @param rankWeight 推荐排序使用的权重
 * @param exclusionRadiusKm 同类商家排他半径，单位公里
 */
public record MerchantAssessmentSnapshotVO(
        Long merchantId,
        String level,
        BigDecimal score,
        BigDecimal commissionRate,
        BigDecimal rankWeight,
        BigDecimal exclusionRadiusKm) {
}
