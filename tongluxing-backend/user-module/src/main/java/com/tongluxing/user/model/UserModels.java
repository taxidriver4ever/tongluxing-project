package com.tongluxing.user.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户域及相关页面聚合模型集合。
 *
 * <p>该类集中放置多个模块复用的请求对象和返回对象，避免简单 DTO 在模块之间重复定义。
 * 其中 Request 主要用于接口入参校验，VO 主要用于接口返回展示。</p>
 */
public final class UserModels {

    /**
     * 工具类不允许实例化。
     */
    private UserModels() {
    }

    /**
     * 通用分页返回结构。
     *
     * @param records 当前页记录
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页数量
     */
    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    /**
     * 修改用户资料请求。
     *
     * <p>字段均为可选，未传入表示保留原值。</p>
     */
    public record UpdateUserProfileRequest(
            @Size(max = 16) String nickname,
            @Size(max = 512) String avatarImageKey,
            @Min(0) @Max(2) Integer gender,
            LocalDate birthday,
            @Size(max = 12) String cityCode,
            @Size(max = 32) String cityName,
            @Size(max = 100) String bio
    ) {
    }

    /** 驾驶证认证提交请求，字段由小程序 OCR 后经用户确认。 */
    public record CertificationRequest(
            @NotBlank @Size(max = 64) String holderName,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{6,32}$") String licenseNo,
            @NotBlank @Size(max = 32) String vehicleClass,
            LocalDate firstIssueDate,
            LocalDate validFrom,
            LocalDate validTo,
            @Size(max = 128) String issuingAuthority,
            @NotBlank @Size(max = 512) String licenseFrontImageKey,
            @Size(max = 512) String licenseBackImageKey,
            @NotBlank @Pattern(regexp = "MINIPROGRAM_OCR|MANUAL_UPLOAD") String recognitionSource
    ) {
    }

    /**
     * 邀请码绑定请求。
     */
    public record InviteBindRequest(@NotBlank @Size(max = 16) String inviteCode) {
    }

    /**
     * 订单锁定优惠券请求。
     */
    public record CouponLockRequest(@NotNull Long orderId, @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    /**
     * 订单支付结果通知请求。
     */
    public record CouponOrderResultRequest(
            @NotNull Long orderId,
            @NotBlank @Pattern(regexp = "SUCCESS|FAILED|CANCELLED") String payStatus
    ) {
    }

    /**
     * 地点请求对象，用于行程起点、终点和途经点。
     */
    public record LocationRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 255) String address,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }

    /**
     * 行程草稿创建/修改请求。
     */
    public record TripDraftRequest(
            @NotNull @Valid LocationRequest startLocation,
            @NotNull @Valid LocationRequest endLocation,
            @Valid List<LocationRequest> waypoints,
            @NotNull LocalDateTime departureTime,
            @NotNull @Min(1) @Max(365) Integer durationDays,
            @NotNull @Min(1) @Max(100) Integer peopleCount,
            @Size(max = 255) String remark
    ) {
    }

    /**
     * 草稿发布请求。
     *
     * <p>publishType 决定发布为普通行程还是组队行程。</p>
     */
    public record PublishDraftRequest(@NotBlank @Pattern(regexp = "TRIP|TEAM") String publishType) {
    }

    /**
     * 当前用户完整资料返回对象。
     */
    public record UserProfileVO(
            Long userId, String nickname, String avatarImageKey, Integer gender, LocalDate birthday,
            String cityCode, String cityName, String bio, String profileStatus, String drivingLicenseCertificationStatus
    ) {
    }

    /** 驾驶证认证状态返回对象。 */
    public record CertificationVO(
            Long certificationId, Long userId, String status, String rejectReason,
            LocalDateTime submittedAt, LocalDateTime reviewedAt, Boolean canResubmit
    ) {
    }

    /** 后台驾驶证认证列表项。 */
    public record DrivingLicenseAuditSummaryVO(
            Long certificationId, Long userId, String holderName, String licenseNoMasked,
            String vehicleClass, LocalDate validTo, String status, LocalDateTime submittedAt
    ) {
    }

    /** 后台驾驶证认证详情，图片 URL 由 admin-module 按需补充。 */
    public record DrivingLicenseAuditDetailVO(
            Long certificationId, Long userId, String holderName, String licenseNo, String vehicleClass,
            LocalDate firstIssueDate, LocalDate validFrom, LocalDate validTo, String issuingAuthority,
            String licenseFrontImageKey, String licenseBackImageKey, String recognitionSource,
            String status, String rejectReason, LocalDateTime submittedAt, LocalDateTime reviewedAt
    ) {
    }

    /**
     * 用户公开主页资料返回对象。
     *
     * <p>只包含允许公开展示的基础信息。</p>
     */
    public record PublicProfileVO(
            Long userId, String nickname, String avatarImageKey, String cityName, String bio,
            String drivingLicenseCertificationStatus, Integer totalTripCount, Long totalDistanceMeters,
            Long totalDurationMinutes, Integer completedWaypointCount
    ) {
        public PublicProfileVO(Long userId, String nickname, String avatarImageKey, String cityName, String bio,
                               String drivingLicenseCertificationStatus) {
            this(userId, nickname, avatarImageKey, cityName, bio, drivingLicenseCertificationStatus, 0, 0L, 0L, 0);
        }
    }

    /** 当前用户与目标用户的关注关系及公开计数。 */
    public record FollowStatusVO(
            Long userId, Boolean following, Boolean followedByTarget, Boolean mutual,
            Long followerCount, Long followingCount
    ) {
    }

    /** 粉丝/关注列表中的公开用户摘要。 */
    public record FollowUserVO(
            Long userId, String nickname, String avatarImageKey, String certificationStatus,
            Integer totalTripCount, Long totalDistanceMeters, LocalDateTime followedAt
    ) {
    }

    /**
     * 成长值概览返回对象。
     */
    public record GrowthSummaryVO(Integer totalPoints, String levelCode, Integer nextLevelPoints) {
    }

    /**
     * 成长值流水返回对象。
     */
    public record GrowthLogVO(
            Long id, String bizType, String bizId, Integer pointDelta, Integer balanceAfter,
            String remark, LocalDateTime createdAt
    ) {
    }

    /**
     * 单个徽章展示对象。
     */
    public record BadgeVO(
            Long badgeId, String badgeCode, String badgeName, String badgeImageKey,
            String conditionDescription, String eventType, Integer currentValue, Integer threshold,
            LocalDateTime awardedAt
    ) {
    }

    /**
     * 徽章墙返回对象。
     *
     * <p>earned 表示已获得徽章，locked 表示尚未获得但可展示的徽章。</p>
     */
    public record BadgeWallVO(List<BadgeVO> earned, List<BadgeVO> locked) {
    }

    /**
     * 用户邀请码返回对象。
     */
    public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
    }

    /**
     * 邀请绑定结果返回对象。
     */
    public record InviteBindVO(Long inviterUserId, Long inviteeUserId, String status, LocalDateTime boundAt) {
    }

    /**
     * 单条邀请关系返回对象。
     */
    public record InvitationVO(
            Long relationId, Long inviteeUserId, String inviteCode, String status,
            LocalDateTime boundAt, LocalDateTime firstTeamCompletedAt
    ) {
    }

    /**
     * 邀请奖励进度返回对象。
     */
    public record InviteRewardProgressVO(Integer validInviteCount, Integer nextRewardNeed, List<String> grantedRuleCodes) {
    }

    /**
     * 优惠券列表项返回对象。
     */
    public record CouponSummaryVO(
            Long id, Long templateId, String couponName, String couponType, BigDecimal thresholdAmount,
            BigDecimal discountAmount, String couponStatus, LocalDateTime validStartAt, LocalDateTime validEndAt
    ) {
    }

    /**
     * 用户优惠券详情返回对象。
     */
    public record UserCouponDetailVO(
            Long id, Long templateId, String couponName, String couponType, Long issuerId,
            BigDecimal thresholdAmount, BigDecimal discountAmount, String scopeJson, String couponStatus,
            LocalDateTime validStartAt, LocalDateTime validEndAt, Long lockedOrderId, Long usedOrderId
    ) {
    }

    /**
     * 当前订单可用优惠券返回对象。
     */
    public record AvailableCouponVO(Long id, String couponName, BigDecimal deductionAmount, LocalDateTime validEndAt) {
    }

    /**
     * 优惠券抵扣结果返回对象。
     */
    public record CouponDeductionVO(Long userCouponId, Long orderId, BigDecimal deductionAmount, String status) {
    }

    /**
     * 地点展示对象。
     */
    public record LocationVO(String name, String address, BigDecimal latitude, BigDecimal longitude) {
    }

    /**
     * 行程草稿返回对象。
     */
    public record TripDraftVO(
            Long draftId, LocationVO startLocation, LocationVO endLocation, List<LocationVO> waypoints,
            LocalDateTime departureTime, Integer durationDays, Integer peopleCount, String remark,
            String draftStatus, Long publishedTripId, LocalDateTime updatedAt
    ) {
    }

    /**
     * 草稿发布结果返回对象。
     */
    public record PublishResultVO(Long draftId, String publishType, Long publishedId) {
    }

    /**
     * 组队匹配结果返回对象。
     */
    public record TeamMatchVO(Long teamId, String teamName, BigDecimal routeOverlapRate, Long timeDifferenceMinutes) {
    }

    /**
     * 用户首页聚合数据返回对象。
     */
    public record UserDashboardVO(
            UserProfileVO profile, GrowthSummaryVO growth, CouponCountVO coupon,
            InvitationSummaryVO invitation, TripDraftVO nextTripDraft
    ) {
    }

    /**
     * 优惠券数量摘要。
     */
    public record CouponCountVO(Integer availableCount, Integer expiringCount) {
    }

    /**
     * 邀请摘要信息。
     */
    public record InvitationSummaryVO(Integer validInviteCount, Integer nextRewardNeed) {
    }

    /**
     * 用户主页聚合数据返回对象。
     */
    public record UserHomepageVO(PublicProfileVO profile, GrowthSummaryVO growth, BadgeWallVO badges,
                                 String ipProvince, FollowStatusVO follow) {
    }

    /**
     * 客服入口返回对象。
     */
    public record CustomerServiceEntryVO(String scene, String channel, String target) {
    }
}
