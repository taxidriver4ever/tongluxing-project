package com.tongluxing;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.invite.integration.InviteRewardPort;

import lombok.RequiredArgsConstructor;

/**
 * 邀请奖励到成长模块的适配器。
 *
 * <p>invite-module 只判断奖励资格和记录奖励状态，实际同路值发放统一通过 growth-module。</p>
 */
@Component
@RequiredArgsConstructor
public class InviteGrowthRewardAdapter implements InviteRewardPort {

    private static final Map<String, Integer> REWARD_POINTS = Map.of(
            "INVITE_STAGE_1", 50,
            "INVITE_STAGE_3", 200,
            "INVITE_STAGE_10", 500,
            "INVITE_STAGE_30", 2000,
            "INVITE_STAGE_50", 5000,
            "FIRST_TEAM", 100
    );

    private final GrowthFacade growthFacade;

    @Override
    public void grantInviteReward(Long beneficiaryUserId, String rewardBizNo, String ruleCode) {
        int points = REWARD_POINTS.getOrDefault(ruleCode, 0);
        if (points <= 0) {
            return;
        }
        growthFacade.grant(beneficiaryUserId, "INVITE", rewardBizNo, points, "邀请奖励");
    }
}
