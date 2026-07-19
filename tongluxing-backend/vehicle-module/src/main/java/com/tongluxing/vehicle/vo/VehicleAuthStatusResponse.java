package com.tongluxing.vehicle.vo;

import java.time.LocalDateTime;

/** 面向车辆认证闭环的状态响应，使用 PENDING/PASS/REJECT 状态。 */
public record VehicleAuthStatusResponse(
        Long applyId,
        Long vehicleId,
        String status,
        String reason,
        LocalDateTime submitTime,
        LocalDateTime auditTime
) {
}
