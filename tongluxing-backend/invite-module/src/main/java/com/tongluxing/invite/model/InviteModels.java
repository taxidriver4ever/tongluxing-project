package com.tongluxing.invite.model;

import java.time.LocalDateTime;
import java.util.List;

/** 邀请模块对外返回模型集合。 */
public final class InviteModels {

    /** 响应模型集合类不允许实例化。 */
    private InviteModels() {
    }

    /**
     * 邀请记录分页响应。
     *
     * @param records 当前页数据
     * @param total 符合筛选条件的总数
     * @param page 规范化后的当前页，从 1 开始
     * @param size 规范化后的每页数量
     */
    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    /**
     * 用户邀请码。
     *
     * @param inviteCode 全平台唯一邀请码
     * @param scene 可直接用于分享链接的查询参数
     * @param enabled 邀请码当前是否启用
     */
    public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
    }

    /**
     * 七天轮换的邀请二维码。
     *
     * @param inviteCode 二维码关联邀请码
     * @param qrToken 带 HMAC 签名的校验 token
     * @param qrContent 编码进二维码的 App deep link
     * @param qrImageBase64 PNG 二维码 Base64 字符串
     * @param generatedAt 当前周期开始时间
     * @param expiresAt token 过期时间
     * @param remainingSeconds 距离过期的剩余秒数
     */
    public record InviteQrVO(
            String inviteCode, String qrToken, String qrContent, String qrImageBase64,
            LocalDateTime generatedAt, LocalDateTime expiresAt, Long remainingSeconds
    ) {
    }

    /**
     * 邀请二维码校验结果。
     *
     * @param valid 签名、时间和邀请码归属是否全部有效
     * @param status VALID、INVALID、EXPIRED 或 DISABLED
     * @param inviteCode 可信场景下解析出的邀请码
     * @param expiresAt token 中的过期时间
     */
    public record InviteQrValidationVO(
            Boolean valid, String status, String inviteCode, LocalDateTime expiresAt
    ) {
    }

    /**
     * 对外展示的邀请人最小资料，不包含用户 ID、手机号等信息。
     *
     * @param nickname 邀请人显示昵称
     * @param avatarUrl 邀请人头像地址
     */
    public record InviterBriefVO(String nickname, String avatarUrl) {
    }

    /**
     * 当前用户邀请绑定资格。
     *
     * <p>bound、eligible、expired 分别表示已绑定、仍可绑定和未绑定但已过期。
     * 已绑定时 inviter 有值，未绑定时 boundAt/inviter 为空。</p>
     */
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

    /** 绑定前预览结果，只含邀请人公开昵称和头像。 */
    public record InvitePreviewVO(
            Boolean valid,
            String inviterNickname,
            String inviterAvatarUrl
    ) {
    }

    /**
     * 绑定成功后仅用于结果展示的奖励快照。
     * 该快照不代表奖励端已实际入账，实际结果以奖励台账为准。
     */
    public record InviteBindRewardVO(
            Integer inviterGrowthValue,
            Integer inviteeGrowthValue,
            Boolean newUserCouponTriggered
    ) {
    }

    /**
     * 邀请码绑定结果。
     *
     * <p>重复提交已有关系时仍返回 bound=true，reward 中不重复展示奖励。</p>
     */
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

    /** 邀请人的单条邀请关系记录。 */
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
     * @param validInviteCount 当前已建立的邀请关系数量
     * @param nextRewardNeed 距离下一展示节点还需邀请的人数
     * @param grantedRuleCodes 已成功发放的奖励规则编码
     */
    public record InviteRewardProgressVO(
            Integer validInviteCount,
            Integer nextRewardNeed,
            List<String> grantedRuleCodes
    ) {
    }
}
