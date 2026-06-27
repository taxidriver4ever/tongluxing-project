package com.tongdao.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 创建车辆请求。
 *
 * <p>车牌号会在服务端做脱敏和简易加密存储，响应中只返回脱敏值。</p>
 */
public record CreateVehicleRequest(
        /** 车牌号，可为空；保存时会生成密文和脱敏值。 */
        @Size(max = 16, message = "车牌号长度不能超过16位")
        String plateNo,
        /** 车辆品牌，例如丰田、比亚迪。 */
        @Size(max = 64, message = "品牌长度不能超过64位")
        String brand,
        /** 车型名称，例如汉 DM-i、Model Y。 */
        @Size(max = 64, message = "车型长度不能超过64位")
        String model,
        /** 车辆类型，例如 SUV、轿车、MPV。 */
        @Size(max = 32, message = "车辆类型长度不能超过32位")
        String vehicleType,
        /** 车辆颜色。 */
        @Size(max = 32, message = "颜色长度不能超过32位")
        String color,
        /** 座位数；为空时服务端默认按 5 座处理。 */
        @Min(value = 1, message = "座位数不能小于1")
        @Max(value = 99, message = "座位数不能大于99")
        Integer seatCount,
        /** 能源类型，例如汽油、混动、纯电。 */
        @Size(max = 32, message = "能源类型长度不能超过32位")
        String energyType,
        /** 车辆照片资源标识，通常为对象存储 key。 */
        @Size(max = 512, message = "车辆照片资源标识长度不能超过512位")
        String vehiclePhotoImageKey
) {
}
