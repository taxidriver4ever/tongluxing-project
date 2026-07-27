package com.tongluxing.invite.dto;

/** 邀请奖励处理结果。 */
public record InviteRewardResult(
        /** 邀请关系 ID；没有邀请关系时为空。 */
        Long relationId,
        /** 奖励受益人，也就是邀请人用户 ID。 */
        Long inviterUserId,
        /** 奖励业务幂等号。 */
        String rewardBizNo,
        /** 处理状态：NO_RELATION、DUPLICATE、ISSUED、FAILED。 */
        String status
) {
}
