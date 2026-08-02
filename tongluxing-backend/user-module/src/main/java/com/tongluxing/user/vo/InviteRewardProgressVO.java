package com.tongluxing.user.vo;

import java.util.List;

/**
 * 邀请奖励进度返回对象。
 *
 * <p>grantedRuleCodes 保存已经发放的规则编码，用于避免重复展示或重复发奖。</p>
 *
 * @param validInviteCount 有效邀请数
 * @param nextRewardNeed 距离下一奖励所需人数
 * @param grantedRuleCodes 已发放规则编码
 */
public record InviteRewardProgressVO(Integer validInviteCount, Integer nextRewardNeed, List<String> grantedRuleCodes) {
}

