package com.tongluxing.vehicle.vo;

/** 按车牌检查车辆是否允许再次提交认证。 */
public record VehicleAuthEligibilityResponse(
        boolean eligible,
        String status,
        String reason
) {
}
