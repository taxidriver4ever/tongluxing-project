package com.tongluxing.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商家考核查询数据对象。
 */
@Data
public class AssessmentQueryDTO {

    private Long id;
    private Long merchantId;
    private Long userId;
    private String merchantName;
    private String period;
    private BigDecimal totalScore;
    private BigDecimal score;
    private String merchantLevel;
    private BigDecimal commissionRate;
    private BigDecimal rankWeight;
    private BigDecimal exclusionRadiusKm;
    private String calculateStatus;
    private LocalDateTime calculatedAt;
    private BigDecimal minScore;
    private BigDecimal maxScore;
}
