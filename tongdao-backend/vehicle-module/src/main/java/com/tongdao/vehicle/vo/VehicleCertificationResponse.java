package com.tongdao.vehicle.vo;

import java.time.LocalDateTime;

public record VehicleCertificationResponse(
        Long vehicleId,
        String ownerName,
        String plateNoMask,
        String vinMask,
        String engineNoMask,
        String licenseImageKey,
        String status,
        String rejectReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
}
