package com.tongdao.vehicle.vo;

/**
 * 车辆详情响应。
 *
 * <p>用于“我的车辆列表”和车辆详情页，所有敏感信息均返回脱敏后的展示字段。</p>
 */
public record VehicleResponse(
        /** 车辆 ID。 */
        Long vehicleId,
        /** 车辆所属用户 ID。 */
        Long userId,
        /** 脱敏后的车牌号。 */
        String plateNoMask,
        /** 车辆品牌。 */
        String brand,
        /** 车型名称。 */
        String model,
        /** 车辆类型。 */
        String vehicleType,
        /** 车辆颜色。 */
        String color,
        /** 座位数。 */
        Integer seatCount,
        /** 能源类型。 */
        String energyType,
        /** 车辆照片资源标识。 */
        String vehiclePhotoImageKey,
        /** 认证状态，例如 UNSUBMITTED、PENDING、APPROVED、REJECTED。 */
        String certificationStatus,
        /** 是否为当前用户默认车辆。 */
        Boolean isDefault
) {
}
