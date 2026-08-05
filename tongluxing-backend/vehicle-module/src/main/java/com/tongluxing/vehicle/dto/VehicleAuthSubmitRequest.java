package com.tongluxing.vehicle.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * P0 车辆认证提交请求。
 *
 * <p>车主认证只要求行驶证图片，不再要求驾驶证前置或车辆外观图。品牌、车型、颜色
 * 可以由用户填写，也可以留空使用默认展示值。</p>
 */
public record VehicleAuthSubmitRequest(
        @Size(max = 64) String vehicleBrand,
        @Size(max = 64) String vehicleModel,
        @NotBlank @Size(max = 16) String plateNumber,
        @Size(max = 32) String vehicleColor,
        /** 旧客户端兼容字段，后端忽略。 */
        @Size(max = 2) List<@NotBlank @Size(max = 512) String> driverLicenseImages,
        /** 行驶证至少一张，允许正页或正副页。 */
        @NotEmpty @Size(min = 1, max = 2) List<@NotBlank @Size(max = 512) String> registrationLicenseImages,
        /** 旧客户端兼容字段，已不再必填；存在时只作为车辆封面。 */
        @Size(max = 3) List<@NotBlank @Size(max = 512) String> vehicleImages
) {
}
