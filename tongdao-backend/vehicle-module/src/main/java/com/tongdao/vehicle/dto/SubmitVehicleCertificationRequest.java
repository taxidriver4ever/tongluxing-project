package com.tongdao.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitVehicleCertificationRequest(
        @Size(max = 64, message = "所有人姓名长度不能超过64位")
        String ownerName,
        @Size(max = 16, message = "车牌号长度不能超过16位")
        String plateNo,
        @Size(max = 32, message = "车架号长度不能超过32位")
        String vin,
        @Size(max = 32, message = "发动机号长度不能超过32位")
        String engineNo,
        @NotBlank(message = "行驶证照片不能为空")
        @Size(max = 512, message = "行驶证照片资源标识长度不能超过512位")
        String licenseImageKey
) {
}
