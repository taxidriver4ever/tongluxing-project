package com.tongluxing.drivertrack.integration;

import com.tongluxing.growth.integration.GrowthFacade.GrowthGrantResult;

/**
 * 驾驶轨迹模块调用成长模块的适配端口。
 */
public interface DriverTrackGrowthPort {

    /**
     * 发放驾驶里程成长值。
     */
    GrowthGrantResult grantMileageGrowth(Long userId, String settleKey, int points, String remark);
}
