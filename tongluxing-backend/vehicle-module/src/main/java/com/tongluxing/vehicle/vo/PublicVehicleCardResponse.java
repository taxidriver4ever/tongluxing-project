package com.tongluxing.vehicle.vo;

/**
 * 公开车辆卡片响应。
 *
 * <p>用于用户主页、行程成员等公开场景，只返回可展示摘要，不返回用户 ID 或敏感明文。</p>
 */
public record PublicVehicleCardResponse(
        /** 车辆 ID。 */
        Long vehicleId,
        /** 车辆品牌。 */
        String brand,
        /** 车型名称。 */
        String model,
        /** 车辆类型。 */
        String vehicleType,
        /** 车辆颜色。 */
        String color,
        /** 脱敏后的车牌号。 */
        String plateNoMask,
        /** 认证状态。 */
        String certificationStatus,
        /** 是否默认车辆。 */
        Boolean isDefault
) {
}
