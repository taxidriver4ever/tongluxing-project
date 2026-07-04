package com.tongluxing.growth.integration;

import com.tongluxing.user.model.UserModels.BadgeWallVO;
import com.tongluxing.user.model.UserModels.GrowthSummaryVO;

/**
 * 成长模块对其他模块开放的门面接口。
 *
 * <p>其他业务模块不直接访问成长模块的 Mapper 或内部实现，只通过该接口查询成长信息、
 * 发放成长值，从而降低模块之间的耦合。</p>
 */
public interface GrowthFacade {

    /**
     * 查询指定用户的成长值概览。
     */
    GrowthSummaryVO getSummary(Long userId);

    /**
     * 查询指定用户的徽章墙。
     */
    BadgeWallVO getBadgeWall(Long userId);

    /**
     * 发放或扣减成长值。
     *
     * @param userId 目标用户 ID
     * @param bizType 业务类型，用于归类流水和触发徽章规则
     * @param bizId 业务唯一标识，用于幂等控制
     * @param points 成长值变化量
     * @param remark 流水备注
     */
    GrowthGrantResult grant(Long userId, String bizType, String bizId, int points, String remark);

    /**
     * 成长值发放结果。
     *
     * @param duplicate 是否为重复业务请求；true 表示已处理过，不再重复发放
     */
    record GrowthGrantResult(Long userId, int pointDelta, int balanceAfter, String levelCode, boolean duplicate) {
    }
}
