package com.tongluxing.invite.integration;

/**
 * 邀请奖励发放端口。
 *
 * <p>invite-module 负责从 invite_reward_rule 读取规则并固化奖励快照，
 * 外部成长模块只按已确认的分值执行幂等发放。</p>
 */
public interface InviteRewardPort {

    /**
     * 发放邀请奖励。
     *
     * @param beneficiaryUserId 奖励受益人用户 ID
     * @param rewardBizNo 奖励业务幂等号
     * @param ruleCode 奖励规则编码
     * @param points 本次规则快照确定的同路值
     */
    void grantInviteReward(Long beneficiaryUserId, String rewardBizNo, String ruleCode, int points);
}
