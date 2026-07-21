package com.tongluxing.vehicle.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 车辆认证闭环提交请求，图片在当前阶段按普通 URL 保存。 */
public record VehicleAuthSubmitRequest(
        @NotBlank @Size(max = 64) String vehicleBrand,
        @NotBlank @Size(max = 64) String vehicleModel,
        @NotBlank @Size(max = 16) String plateNumber,
        @NotBlank @Size(max = 32) String vehicleColor,
        /**
         * 旧版兼容字段，车辆认证不再要求也不会处理驾驶证图片。
         * 驾驶证请通过 /v1/users/me/certifications 独立提交。
         */
        @Size(max = 2) List<@NotBlank @Size(max = 512) String> driverLicenseImages,
        @NotEmpty @Size(min = 2, max = 2) List<@NotBlank @Size(max = 512) String> registrationLicenseImages,
        @NotEmpty @Size(min = 1, max = 3) List<@NotBlank @Size(max = 512) String> vehicleImages
) {
}
