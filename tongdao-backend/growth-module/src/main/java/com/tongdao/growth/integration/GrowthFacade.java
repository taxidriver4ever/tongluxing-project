package com.tongdao.growth.integration;

import com.tongdao.user.model.UserModels.BadgeWallVO;
import com.tongdao.user.model.UserModels.GrowthSummaryVO;

public interface GrowthFacade {
    GrowthSummaryVO getSummary(Long userId);
    BadgeWallVO getBadgeWall(Long userId);
    GrowthGrantResult grant(Long userId, String bizType, String bizId, int points, String remark);
    record GrowthGrantResult(Long userId, int pointDelta, int balanceAfter, String levelCode, boolean duplicate) {}
}
