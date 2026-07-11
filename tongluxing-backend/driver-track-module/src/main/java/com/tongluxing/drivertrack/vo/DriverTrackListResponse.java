package com.tongluxing.drivertrack.vo;

import java.util.List;

/**
 * 行程实际轨迹响应。
 */
public record DriverTrackListResponse(
        String tripId,
        List<DriverTrackPointVO> points
) {
}
