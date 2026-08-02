package com.tongluxing.vehicle.vo;

import java.time.LocalDateTime;

/**
 * 后台车辆认证列表项。
 *
 * @param certificationId 认证申请 ID
 * @param vehicleId 关联车辆 ID
 * @param userId 提交用户 ID
 * @param ownerName 行驶证所有人姓名
 * @param plateNoMask 脱敏车牌号，列表不解密明文
 * @param vehicleType 行驶证车辆类型
 * @param status 数据库审核状态
 * @param submittedAt 提交时间
 */
public record VehicleCertificationAuditSummaryVO(
        Long certificationId, Long vehicleId, Long userId, String ownerName,
        String plateNoMask, String vehicleType, String status, LocalDateTime submittedAt
) {
}
