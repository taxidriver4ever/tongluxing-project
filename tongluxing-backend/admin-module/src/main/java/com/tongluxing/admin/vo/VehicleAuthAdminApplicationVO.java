package com.tongluxing.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 后台车辆认证申请，包含审核所需全部普通图片 URL。 */
public record VehicleAuthAdminApplicationVO(
        Long applyId,
        Long userId,
        Long vehicleId,
        String vehicleBrand,
        String vehicleModel,
        String vehicleColor,
        String plateNumber,
        List<String> driverLicenseImages,
        List<String> registrationLicenseImages,
        List<String> vehicleImages,
        String status,
        String rejectReason,
        LocalDateTime submitTime,
        LocalDateTime auditTime
) {
}
