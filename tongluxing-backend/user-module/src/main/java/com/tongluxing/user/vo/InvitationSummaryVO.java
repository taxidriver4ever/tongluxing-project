package com.tongluxing.user.vo;

/**
 * 邀请摘要信息。
 *
 * @param validInviteCount 已满足规则的有效邀请数
 * @param nextRewardNeed 距离下一档奖励仍需邀请的人数
 */
public record InvitationSummaryVO(Integer validInviteCount, Integer nextRewardNeed) {
}

