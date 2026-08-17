package com.tongluxing.application.adapter;

import org.springframework.stereotype.Component;

import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.invite.integration.InviteRewardPort;

import lombok.RequiredArgsConstructor;

/** 邀请奖励到成长模块的适配器。 */
@Component
@RequiredArgsConstructor
public class InviteGrowthRewardAdapter implements InviteRewardPort {

    private final GrowthFacade growthFacade;

    @Override
    public void grantInviteReward(
            Long beneficiaryUserId, String rewardBizNo, String ruleCode, int points) {
        if (points <= 0) {
            return;
        }
        boolean registerReward = "INVITE_REGISTER_SUCCESS".equals(ruleCode);
        boolean firstTeamReward = "INVITEE_FIRST_TEAM_COMPLETED".equals(ruleCode);
        growthFacade.grant(
                beneficiaryUserId,
                registerReward ? "INVITE_USER_REGISTER" : "INVITE",
                rewardBizNo,
                points,
                registerReward ? "邀请新用户注册奖励"
                        : firstTeamReward ? "被邀请人首次完成有效组队奖励" : "邀请阶梯奖励");
    }
}
