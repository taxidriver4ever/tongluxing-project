package com.tongluxing.drivertrack.integration;

import org.springframework.stereotype.Component;

import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.growth.integration.GrowthFacade.GrowthGrantResult;

import lombok.RequiredArgsConstructor;

/**
 * 驾驶轨迹模块到成长模块的适配器。
 */
@Component
@RequiredArgsConstructor
public class DriverTrackGrowthAdapter implements DriverTrackGrowthPort {

    private static final String BIZ_TYPE_TRIP_MILEAGE = "TRIP_MILEAGE";

    private final GrowthFacade growthFacade;

    @Override
    public GrowthGrantResult grantMileageGrowth(Long userId, String settleKey, int points, String remark) {
        return growthFacade.grant(userId, BIZ_TYPE_TRIP_MILEAGE, settleKey, points, remark);
    }
}
