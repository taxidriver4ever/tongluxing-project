package com.tongluxing.admin.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 后台车辆认证审核详情。 */
public record AdminVehicleCertificationDetailVO(
        Long certificationId, Long vehicleId, Long userId, String ownerName, String plateNo,
        String vehicleBrand, String vehicleModel, String vehicleColor, String vehicleType,
        String vin, String engineNo, LocalDate registerDate, LocalDate issueDate,
        String issuingAuthority, String licenseFrontImageKey, String licenseFrontImageUrl,
        String licenseBackImageKey, String licenseBackImageUrl,
        List<AdminVehicleCertificationImageVO> vehicleImages, String recognitionSource,
        String status, String rejectReason, LocalDateTime submittedAt, LocalDateTime reviewedAt
) {
}
