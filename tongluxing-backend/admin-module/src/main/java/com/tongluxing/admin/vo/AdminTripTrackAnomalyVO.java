package com.tongluxing.admin.vo;

import java.time.LocalDateTime;

/** 后台轨迹异常事件。 */
public record AdminTripTrackAnomalyVO(
        Long id,
        Long userId,
        Long previousPointId,
        Long currentPointId,
        String anomalyType,
        Integer riskScore,
        String detailJson,
        LocalDateTime occurredAt
) {
}
