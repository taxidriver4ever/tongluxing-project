package com.tongluxing.admin.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 后台驾驶证审核详情。 */
public record AdminDrivingLicenseDetailVO(
        Long certificationId, Long userId, String holderName, String licenseNo, String vehicleClass,
        LocalDate firstIssueDate, LocalDate validFrom, LocalDate validTo, String issuingAuthority,
        String licenseFrontImageKey, String licenseFrontImageUrl,
        String licenseBackImageKey, String licenseBackImageUrl,
        String recognitionSource, String status, String rejectReason,
        LocalDateTime submittedAt, LocalDateTime reviewedAt
) {
}
