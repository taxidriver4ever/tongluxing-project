package com.tongdao.user.model;

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

public final class UserModels {

    private UserModels() {
    }

    public record PageResult<T>(List<T> records, long total, int page, int size) {
    }

    public record UpdateUserProfileRequest(
            @Size(max = 32) String nickname,
            @Size(max = 512) String avatarImageKey,
            @Min(0) @Max(2) Integer gender,
            LocalDate birthday,
            @Size(max = 16) String cityCode,
            @Size(max = 64) String cityName,
            @Size(max = 200) String bio
    ) {
    }

    public record CertificationRequest(
            @NotBlank @Size(max = 64) String realName,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{15,18}$") String idCardNo,
            @NotBlank @Size(max = 512) String drivingLicenseImageKey,
            @NotBlank @Size(max = 512) String faceImageKey
    ) {
    }

    public record InviteBindRequest(@NotBlank @Size(max = 16) String inviteCode) {
    }

    public record CouponLockRequest(@NotNull Long orderId, @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    public record CouponOrderResultRequest(
            @NotNull Long orderId,
            @NotBlank @Pattern(regexp = "SUCCESS|FAILED|CANCELLED") String payStatus
    ) {
    }

    public record LocationRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 255) String address,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }

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

    public record PublishDraftRequest(@NotBlank @Pattern(regexp = "TRIP|TEAM") String publishType) {
    }

    public record UserProfileVO(
            Long userId, String nickname, String avatarImageKey, Integer gender, LocalDate birthday,
            String cityCode, String cityName, String bio, String profileStatus, String certificationStatus
    ) {
    }

    public record CertificationVO(
            Long certificationId, Long userId, String certificationStatus, String rejectReason,
            LocalDateTime submittedAt, LocalDateTime reviewedAt
    ) {
    }

    public record PublicProfileVO(
            Long userId, String nickname, String avatarImageKey, String cityName, String bio,
            String certificationStatus
    ) {
    }

    public record GrowthSummaryVO(Integer totalPoints, String levelCode, Integer nextLevelPoints) {
    }

    public record GrowthLogVO(
            Long id, String bizType, String bizId, Integer pointDelta, Integer balanceAfter,
            String remark, LocalDateTime createdAt
    ) {
    }

    public record BadgeVO(Long badgeId, String badgeCode, String badgeName, String badgeImageKey, LocalDateTime awardedAt) {
    }

    public record BadgeWallVO(List<BadgeVO> earned, List<BadgeVO> locked) {
    }

    public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
    }

    public record InviteBindVO(Long inviterUserId, Long inviteeUserId, String status, LocalDateTime boundAt) {
    }

    public record InvitationVO(
            Long relationId, Long inviteeUserId, String inviteCode, String status,
            LocalDateTime boundAt, LocalDateTime firstTeamCompletedAt
    ) {
    }

    public record InviteRewardProgressVO(Integer validInviteCount, Integer nextRewardNeed, List<String> grantedRuleCodes) {
    }

    public record CouponSummaryVO(
            Long id, Long templateId, String couponName, String couponType, BigDecimal thresholdAmount,
            BigDecimal discountAmount, String couponStatus, LocalDateTime validStartAt, LocalDateTime validEndAt
    ) {
    }

    public record UserCouponDetailVO(
            Long id, Long templateId, String couponName, String couponType, Long issuerId,
            BigDecimal thresholdAmount, BigDecimal discountAmount, String scopeJson, String couponStatus,
            LocalDateTime validStartAt, LocalDateTime validEndAt, Long lockedOrderId, Long usedOrderId
    ) {
    }

    public record AvailableCouponVO(Long id, String couponName, BigDecimal deductionAmount, LocalDateTime validEndAt) {
    }

    public record CouponDeductionVO(Long userCouponId, Long orderId, BigDecimal deductionAmount, String status) {
    }

    public record LocationVO(String name, String address, BigDecimal latitude, BigDecimal longitude) {
    }

    public record TripDraftVO(
            Long draftId, LocationVO startLocation, LocationVO endLocation, List<LocationVO> waypoints,
            LocalDateTime departureTime, Integer durationDays, Integer peopleCount, String remark,
            String draftStatus, Long publishedTripId, LocalDateTime updatedAt
    ) {
    }

    public record PublishResultVO(Long draftId, String publishType, Long publishedId) {
    }

    public record TeamMatchVO(Long teamId, String teamName, BigDecimal routeOverlapRate, Long timeDifferenceMinutes) {
    }

    public record UserDashboardVO(
            UserProfileVO profile, GrowthSummaryVO growth, CouponCountVO coupon,
            InvitationSummaryVO invitation, TripDraftVO nextTripDraft
    ) {
    }

    public record CouponCountVO(Integer availableCount, Integer expiringCount) {
    }

    public record InvitationSummaryVO(Integer validInviteCount, Integer nextRewardNeed) {
    }

    public record UserHomepageVO(PublicProfileVO profile, GrowthSummaryVO growth, BadgeWallVO badges) {
    }

    public record CustomerServiceEntryVO(String scene, String channel, String target) {
    }
}
