package com.tongluxing.vehicle.vo;

/** 按车牌检查车辆是否允许再次提交认证。 */
public record VehicleAuthEligibilityResponse(
        /** 当前用户与车牌组合是否允许提交认证。 */
        boolean eligible,
        /** 阻断时的相关认证状态，如 UNSUBMITTED 或 APPROVED。 */
        String status,
        /** 不可提交时给前端展示的原因；可提交时为空串。 */
        String reason
) {
}
