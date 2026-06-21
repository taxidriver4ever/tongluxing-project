package com.tongdao.growth.service;
import com.tongdao.growth.integration.GrowthFacade;
import com.tongdao.user.model.UserModels.*;
public interface GrowthService extends GrowthFacade {
    GrowthSummaryVO getCurrentSummary();
    PageResult<GrowthLogVO> getCurrentLogs(int page,int size);
    BadgeWallVO getCurrentBadges();
}
