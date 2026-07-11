package com.tongluxing.vehicle.vo;

import java.time.LocalDateTime;

/** 后台车辆认证列表项。 */
public record VehicleCertificationAuditSummaryVO(
        Long certificationId, Long vehicleId, Long userId, String ownerName,
        String plateNoMask, String vehicleType, String status, LocalDateTime submittedAt
) {
}
