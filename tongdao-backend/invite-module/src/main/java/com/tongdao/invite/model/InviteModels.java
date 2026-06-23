package com.tongdao.invite.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邀请模块对外返回模型集合。
 *
 * <p>这些 record 只描述 invite-module 自身的接口返回结构，避免邀请业务继续复用 user-module 的模型。</p>
 */
public final class InviteModels {

    private InviteModels() {
    }

    /**
     * 分页结果。
     *
     * @param records 当前页数据
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页数量
     * @param <T> 记录类型
     */
    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    /**
     * 当前用户邀请码信息。
     *
     * @param inviteCode 邀请码
     * @param scene 分享场景参数
     * @param enabled 邀请码是否启用
     */
    public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
    }

    /**
     * 邀请码绑定结果。
     *
     * @param inviterUserId 邀请人用户 ID
     * @param inviteeUserId 被邀请人用户 ID
     * @param status 绑定状态
     * @param boundAt 绑定时间
     */
    public record InviteBindVO(Long inviterUserId, Long inviteeUserId, String status, LocalDateTime boundAt) {
    }

    /**
     * 邀请记录。
     *
     * @param relationId 邀请关系 ID
     * @param inviteeUserId 被邀请人用户 ID
     * @param inviteCode 绑定时使用的邀请码
     * @param status 邀请关系状态
     * @param boundAt 绑定时间
     * @param firstTeamCompletedAt 被邀请人首次完成组队时间
     */
    public record InvitationVO(
            Long relationId,
            Long inviteeUserId,
            String inviteCode,
            String status,
            LocalDateTime boundAt,
            LocalDateTime firstTeamCompletedAt
    ) {
    }

    /**
     * 邀请奖励进度。
     *
     * @param validInviteCount 有效邀请数
     * @param nextRewardNeed 距离下一档奖励还差多少有效邀请
     * @param grantedRuleCodes 已发放奖励规则编码
     */
    public record InviteRewardProgressVO(
            Integer validInviteCount,
            Integer nextRewardNeed,
            List<String> grantedRuleCodes
    ) {
    }
}
