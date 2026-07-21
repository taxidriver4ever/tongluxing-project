package com.tongluxing.vehicle.vo;

import java.time.LocalDateTime;

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
        Boolean isDefault,
        /** 最近一次认证申请 ID；未提交认证时为空。 */
        Long certificationId,
        /** 最近一次认证驳回原因；非驳回状态为空。 */
        String rejectReason,
        /** 最近一次认证提交时间。 */
        LocalDateTime certificationSubmittedAt,
        /** 最近一次认证审核时间。 */
        LocalDateTime certificationReviewedAt
) {
}
