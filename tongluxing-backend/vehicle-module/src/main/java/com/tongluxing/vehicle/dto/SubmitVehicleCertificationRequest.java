package com.tongluxing.vehicle.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 提交车辆认证请求。
 *
 * <p>认证资料用于后台审核，涉及敏感字段的内容只返回脱敏结果。</p>
 */
public record SubmitVehicleCertificationRequest(
        /** 行驶证所有人姓名。 */
        @NotBlank(message = "所有人姓名不能为空")
        @Size(max = 64, message = "所有人姓名长度不能超过64位")
        String ownerName,
        /** 待认证车牌号。 */
        @NotBlank(message = "车牌号不能为空")
        @Size(max = 16, message = "车牌号长度不能超过16位")
        String plateNo,
        /** 行驶证车辆类型。 */
        @NotBlank(message = "车辆类型不能为空")
        @Size(max = 32, message = "车辆类型长度不能超过32位")
        String vehicleType,
        /** 车辆识别代号 VIN。 */
        @NotBlank(message = "车架号不能为空")
        @Size(max = 32, message = "车架号长度不能超过32位")
        String vin,
        /** 发动机号。 */
        @NotBlank(message = "发动机号不能为空")
        @Size(max = 32, message = "发动机号长度不能超过32位")
        String engineNo,
        /** 注册日期与发证日期。 */
        LocalDate registerDate,
        LocalDate issueDate,
        /** 发证机关。 */
        @Size(max = 128, message = "发证机关长度不能超过128位")
        String issuingAuthority,
        /** 行驶证正页资源标识。 */
        @NotBlank(message = "行驶证正页不能为空")
        @Size(max = 512, message = "行驶证正页资源标识长度不能超过512位")
        String licenseFrontImageKey,
        /** 行驶证副页资源标识。 */
        @Size(max = 512, message = "行驶证副页资源标识长度不能超过512位")
        String licenseBackImageKey,
        /** 车辆审核图片。 */
        @Valid @NotEmpty(message = "至少上传一张车辆图片") @Size(max = 8, message = "车辆图片不能超过8张")
        List<VehicleImageRequest> vehicleImages,
        /** 字段识别来源。 */
        @NotBlank @Pattern(regexp = "MINIPROGRAM_OCR|MANUAL_UPLOAD", message = "识别来源仅支持MINIPROGRAM_OCR或MANUAL_UPLOAD")
        String recognitionSource
) {
    /** 单张车辆审核图片。 */
    public record VehicleImageRequest(
            @NotBlank @Pattern(regexp = "DRIVER_LICENSE|REGISTRATION_LICENSE|VEHICLE|VEHICLE_FRONT|VEHICLE_REAR|VEHICLE_SIDE|VEHICLE_OTHER") String imageType,
            @NotBlank @Size(max = 512) String imageKey
    ) {
    }
}
