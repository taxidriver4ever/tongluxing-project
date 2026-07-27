package com.tongluxing.invite.model;

import java.time.LocalDateTime;
import java.util.List;

/** 邀请模块对外返回模型集合。 */
public final class InviteModels {

    private InviteModels() {
    }

    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
    }

    /** 七天轮换的邀请二维码。 */
    public record InviteQrVO(
            String inviteCode, String qrToken, String qrContent, String qrImageBase64,
            LocalDateTime generatedAt, LocalDateTime expiresAt, Long remainingSeconds
    ) {
    }

    public record InviteQrValidationVO(
            Boolean valid, String status, String inviteCode, LocalDateTime expiresAt
    ) {
    }

    /** 对外展示的邀请人最小资料，不包含手机号等敏感信息。 */
    public record InviterBriefVO(String nickname, String avatarUrl) {
    }

    /** 当前用户邀请绑定资格。 */
    public record InviteBindStatusVO(
            Boolean bound,
            Boolean eligible,
            Boolean expired,
            LocalDateTime registeredAt,
            LocalDateTime expireAt,
            Long remainingSeconds,
            LocalDateTime boundAt,
            InviterBriefVO inviter
    ) {
    }

    /** 绑定前预览结果。 */
    public record InvitePreviewVO(
            Boolean valid,
            String inviterNickname,
            String inviterAvatarUrl
    ) {
    }

    /** 绑定成功后仅用于结果展示的奖励快照。 */
    public record InviteBindRewardVO(
            Integer inviterGrowthValue,
            Integer inviteeGrowthValue,
            Boolean newUserCouponTriggered
    ) {
    }

    /** 邀请码绑定结果。 */
    public record InviteBindVO(
            Boolean bound,
            Long relationId,
            Long inviterUserId,
            Long inviteeUserId,
            String status,
            LocalDateTime boundAt,
            InviteBindRewardVO reward
    ) {
    }

    public record InvitationVO(
            Long relationId,
            Long inviteeUserId,
            String inviteCode,
            String status,
            LocalDateTime boundAt,
            LocalDateTime firstTeamCompletedAt
    ) {
    }

    public record InviteRewardProgressVO(
            Integer validInviteCount,
            Integer nextRewardNeed,
            List<String> grantedRuleCodes
    ) {
    }
}
