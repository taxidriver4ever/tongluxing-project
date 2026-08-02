package com.tongluxing.growth.service;

import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.common.model.PageResult;
import com.tongluxing.user.vo.BadgeWallVO;
import com.tongluxing.user.vo.GrowthLogVO;
import com.tongluxing.user.vo.GrowthSummaryVO;

/**
 * 成长模块业务服务。
 *
 * <p>在 {@link GrowthFacade} 的跨模块能力基础上，补充当前登录用户维度的查询接口，
 * Controller 只依赖该服务完成参数接收和结果返回。</p>
 */
public interface GrowthService extends GrowthFacade {

    /**
     * 查询当前登录用户的成长值、等级和距离下一等级所需成长值。
     */
    GrowthSummaryVO getCurrentSummary();

    /**
     * 分页查询当前登录用户的成长值流水。
     */
    PageResult<GrowthLogVO> getCurrentLogs(int page, int size);

    /**
     * 查询当前登录用户的徽章墙。
     */
    BadgeWallVO getCurrentBadges();
}
