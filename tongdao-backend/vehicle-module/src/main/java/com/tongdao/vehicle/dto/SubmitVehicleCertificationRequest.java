package com.tongdao.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交车辆认证请求。
 *
 * <p>认证资料用于后台审核，涉及敏感字段的内容只返回脱敏结果。</p>
 */
public record SubmitVehicleCertificationRequest(
        /** 行驶证所有人姓名。 */
        @Size(max = 64, message = "所有人姓名长度不能超过64位")
        String ownerName,
        /** 待认证车牌号。 */
        @Size(max = 16, message = "车牌号长度不能超过16位")
        String plateNo,
        /** 车辆识别代号 VIN。 */
        @Size(max = 32, message = "车架号长度不能超过32位")
        String vin,
        /** 发动机号。 */
        @Size(max = 32, message = "发动机号长度不能超过32位")
        String engineNo,
        /** 行驶证照片资源标识。 */
        @NotBlank(message = "行驶证照片不能为空")
        @Size(max = 512, message = "行驶证照片资源标识长度不能超过512位")
        String licenseImageKey
) {
}
