package com.tongluxing.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家考核查询数据对象。
 *
 * <p>该 DTO 复用在多条 SQL 查询中，既承载商家资料快照，也承载考核分和等级映射配置。
 * 字段可能只在特定查询中有值，Service 层需要按调用场景读取。</p>
 */
@Data
public class AssessmentQueryDTO {

    /** 考核分记录或配置记录主键。 */
    private Long id;

    /** 商家 ID。 */
    private Long merchantId;

    /** 商家所属用户 ID。 */
    private Long userId;

    /** 商家名称。 */
    private String merchantName;

    /** 考核周期，格式为 yyyy-MM。 */
    private String period;

    /** 指定周期计算出的总分。 */
    private BigDecimal totalScore;

    /** 商家资料表中的当前快照分。 */
    private BigDecimal score;

    /** 商家等级编码，例如 L1、L2。 */
    private String merchantLevel;

    /** 当前等级对应的平台佣金率。 */
    private BigDecimal commissionRate;

    /** 当前等级对应的推荐排序权重。 */
    private BigDecimal rankWeight;

    /** 当前等级对应的同类商家排他半径，单位公里。 */
    private BigDecimal exclusionRadiusKm;

    /** 考核计算状态。 */
    private String calculateStatus;

    /** 本次考核计算完成时间。 */
    private LocalDateTime calculatedAt;

    /** 等级配置最低分。 */
    private BigDecimal minScore;

    /** 等级配置最高分。 */
    private BigDecimal maxScore;
}
