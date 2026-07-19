package com.tongluxing.vehicle.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 后台车辆认证详情，包含授权审核所需的原始字段。 */
public record VehicleCertificationAuditDetailVO(
        Long certificationId, Long vehicleId, Long userId, String ownerName, String plateNo,
        String vehicleBrand, String vehicleModel, String vehicleColor, String vehicleType,
        String vin, String engineNo, LocalDate registerDate, LocalDate issueDate,
        String issuingAuthority, String licenseFrontImageKey, String licenseBackImageKey,
        List<VehicleCertificationImageVO> vehicleImages, String recognitionSource, String status,
        String rejectReason, LocalDateTime submittedAt, LocalDateTime reviewedAt
) {
}
