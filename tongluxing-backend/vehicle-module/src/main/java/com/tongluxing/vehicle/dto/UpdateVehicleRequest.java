package com.tongluxing.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 更新车辆资料请求。
 *
 * <p>当前更新接口只允许修改车辆展示资料，不修改车牌号和认证状态。</p>
 */
public record UpdateVehicleRequest(
        /** 车辆品牌。 */
        @Size(max = 64, message = "品牌长度不能超过64位")
        String brand,
        /** 车型名称。 */
        @Size(max = 64, message = "车型长度不能超过64位")
        String model,
        /** 车辆类型。 */
        @Size(max = 32, message = "车辆类型长度不能超过32位")
        String vehicleType,
        /** 车辆颜色。 */
        @Size(max = 32, message = "颜色长度不能超过32位")
        String color,
        /** 座位数；为空时保持原值。 */
        @Min(value = 1, message = "座位数不能小于1")
        @Max(value = 99, message = "座位数不能大于99")
        Integer seatCount,
        /** 能源类型。 */
        @Size(max = 32, message = "能源类型长度不能超过32位")
        String energyType,
        /** 车辆照片资源标识。 */
        @Size(max = 512, message = "车辆照片资源标识长度不能超过512位")
        String vehiclePhotoImageKey
) {
}
