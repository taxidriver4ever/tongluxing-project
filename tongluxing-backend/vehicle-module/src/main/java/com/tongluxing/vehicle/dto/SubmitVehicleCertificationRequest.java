package com.tongluxing.vehicle.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 标准车辆认证请求。
 *
 * <p>P0 只把车牌和行驶证图片作为硬要求。OCR 未识别出的所有人、VIN、发动机号等字段
 * 可以为空，不能再阻塞认证。</p>
 */
public record SubmitVehicleCertificationRequest(
        @Size(max = 64) String ownerName,
        @NotBlank(message = "车牌号不能为空") @Size(max = 16) String plateNo,
        @Size(max = 32) String vehicleType,
        @Size(max = 32) String vin,
        @Size(max = 32) String engineNo,
        LocalDate registerDate,
        LocalDate issueDate,
        @Size(max = 128) String issuingAuthority,
        @NotBlank(message = "行驶证图片不能为空") @Size(max = 512) String licenseFrontImageKey,
        @Size(max = 512) String licenseBackImageKey,
        @Valid @Size(max = 8) List<VehicleImageRequest> vehicleImages,
        @NotBlank @Pattern(regexp = "MINIPROGRAM_OCR|MANUAL_UPLOAD") String recognitionSource
) {
    public record VehicleImageRequest(
            @NotBlank @Pattern(regexp = "REGISTRATION_LICENSE|VEHICLE|VEHICLE_FRONT|VEHICLE_REAR|VEHICLE_SIDE|VEHICLE_OTHER") String imageType,
            @NotBlank @Size(max = 512) String imageKey
    ) {
    }
}
