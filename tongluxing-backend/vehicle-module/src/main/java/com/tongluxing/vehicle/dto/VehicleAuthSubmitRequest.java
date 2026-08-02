package com.tongluxing.vehicle.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 车辆认证闭环提交请求。
 *
 * <p>该请求同时携带车辆基础资料、行驶证图片和车辆外观图。
 * 服务端会创建或复用车辆档案，然后转换为标准认证提交命令。</p>
 */
public record VehicleAuthSubmitRequest(
        /** 车辆品牌，用于同步车辆档案的公开展示信息。 */
        @NotBlank @Size(max = 64) String vehicleBrand,
        /** 车型名称。 */
        @NotBlank @Size(max = 64) String vehicleModel,
        /** 车牌号；服务端会规范化、加密、脱敏并执行全平台去重。 */
        @NotBlank @Size(max = 16) String plateNumber,
        /** 车辆颜色。 */
        @NotBlank @Size(max = 32) String vehicleColor,
        /**
         * 旧版兼容字段，车辆认证不再要求也不会处理驾驶证图片。
         * 驾驶证请通过 /v1/users/me/certifications 独立提交。
         */
        @Size(max = 2) List<@NotBlank @Size(max = 512) String> driverLicenseImages,
        /** 行驶证正页和副页，固定两张，顺序不可交换。 */
        @NotEmpty @Size(min = 2, max = 2) List<@NotBlank @Size(max = 512) String> registrationLicenseImages,
        /** 车辆外观图，允许 1~3 张，第一张同时作为车辆卡片封面。 */
        @NotEmpty @Size(min = 1, max = 3) List<@NotBlank @Size(max = 512) String> vehicleImages
) {
}
