package com.tongdao.invite.integration;

/**
 * 邀请奖励发放端口。
 *
 * <p>invite-module 只负责识别奖励资格和记录发放状态，具体发放成长值、优惠券等动作由实现该端口的外部模块完成。</p>
 */
public interface InviteRewardPort {

    /**
     * 发放邀请奖励。
     *
     * @param beneficiaryUserId 奖励受益人用户 ID
     * @param rewardBizNo 奖励业务幂等号
     * @param ruleCode 奖励规则编码
     */
    void grantInviteReward(Long beneficiaryUserId, String rewardBizNo, String ruleCode);
}
