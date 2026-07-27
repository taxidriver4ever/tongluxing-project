package com.tongluxing.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台地图轨迹点，只读展示原始与过滤结果。 */
public record AdminTripTrackPointVO(
        Long id,
        Long userId,
        Long sequenceNo,
        BigDecimal longitude,
        BigDecimal latitude,
        BigDecimal accuracyMeters,
        BigDecimal calculatedSpeedKmh,
        Integer rawDistanceMeters,
        Integer acceptedDistanceMeters,
        String pointStatus,
        Integer riskScore,
        String riskFlags,
        String rejectReason,
        Integer mockLocation,
        LocalDateTime locationTime
) {
}
