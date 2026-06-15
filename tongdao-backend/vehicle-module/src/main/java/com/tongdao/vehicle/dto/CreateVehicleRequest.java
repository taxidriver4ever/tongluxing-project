package com.tongdao.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
        @Size(max = 16, message = "车牌号长度不能超过16位")
        String plateNo,
        @Size(max = 64, message = "品牌长度不能超过64位")
        String brand,
        @Size(max = 64, message = "车型长度不能超过64位")
        String model,
        @Size(max = 32, message = "车辆类型长度不能超过32位")
        String vehicleType,
        @Size(max = 32, message = "颜色长度不能超过32位")
        String color,
        @Min(value = 1, message = "座位数不能小于1")
        @Max(value = 99, message = "座位数不能大于99")
        Integer seatCount,
        @Size(max = 32, message = "能源类型长度不能超过32位")
        String energyType,
        @Size(max = 512, message = "车辆照片资源标识长度不能超过512位")
        String vehiclePhotoImageKey
) {
}
