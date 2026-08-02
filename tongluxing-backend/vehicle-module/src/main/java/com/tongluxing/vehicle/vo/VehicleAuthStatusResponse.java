package com.tongluxing.vehicle.vo;

import java.time.LocalDateTime;

/** 面向车辆认证闭环的状态响应，使用 PENDING/PASS/REJECT 状态。 */
public record VehicleAuthStatusResponse(
        /** 最新认证申请 ID；未提交时为空。 */
        Long applyId,
        /** 关联车辆 ID。 */
        Long vehicleId,
        /** 产品层状态：UNSUBMITTED、PENDING、PASS 或 REJECT。 */
        String status,
        /** 驳回原因；非 REJECT 状态通常为空。 */
        String reason,
        /** 申请提交时间。 */
        LocalDateTime submitTime,
        /** 审核完成时间；待审状态为空。 */
        LocalDateTime auditTime
) {
}
