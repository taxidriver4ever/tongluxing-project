package com.tongluxing.drivertrack.vo;

import java.math.BigDecimal;

/**
 * 驾驶轨迹点展示对象。
 */
public record DriverTrackPointVO(
        String id,
        String tripId,
        String driverId,
        BigDecimal longitude,
        BigDecimal latitude,
        BigDecimal speed,
        BigDecimal direction,
        BigDecimal accuracy,
        Integer distanceFromPrev,
        String recordTime
) {
}
