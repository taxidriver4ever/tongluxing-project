package com.tongluxing.trip.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.trip.dto.TripTrackReviewRequest;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripMemberSnapshot;
import com.tongluxing.trip.entity.TripTrackReviewSnapshot;
import com.tongluxing.trip.mapper.TripAuditLogMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
import com.tongluxing.trip.mapper.TripExecutionSettlementMapper;
import com.tongluxing.trip.service.TripSettlementService;
import com.tongluxing.trip.support.TripGrowthCalculator;
import com.tongluxing.trip.vo.TripSettlementResponse;
import com.tongluxing.trip.vo.TripTrackReviewResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 行程成长值结算实现。
 *
 * <p>结束接口只推进到 FINISHED；本服务锁定行程后发放幂等成长值，全部成功后推进到 SETTLED。
 * growth_log 的业务唯一键和 trip 行锁共同保证重复请求不会重复奖励。</p>
 */
@Service
@RequiredArgsConstructor
public class TripSettlementServiceImpl implements TripSettlementService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CurrentUserContext currentUserContext;
    private final TripMapper tripMapper;
    private final TripMemberSnapshotMapper memberMapper;
    private final TripExecutionSettlementMapper executionSettlementMapper;
    private final TripAuditLogMapper auditLogMapper;
    private final InviteFacade inviteFacade;
    private final GrowthFacade growthFacade;
    private final StringRedisTemplate redisTemplate;

    @Value("${trajectory.destination-radius-meters:1000}")
    private int destinationRadiusMeters;
    @Value("${trajectory.destination-min-points:1}")
    private int destinationMinPoints;
    @Value("${trajectory.destination-min-duration-seconds:0}")
    private int destinationMinDurationSeconds;
    @Value("${trajectory.gap-segment-max-seconds:60}")
    private int fatalArrivalCooldownSeconds;

    @Override
    @Transactional
    public TripSettlementResponse settle(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip existing = tripMapper.findById(tripId);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        Long captainUserId = existing.getCaptainUserId() == null
                ? existing.getUserId() : existing.getCaptainUserId();
        if (!userId.equals(captainUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有当前行程队长可以结束并结算行程");
        }
        Trip trip = tripMapper.findOwnedForUpdate(tripId, existing.getUserId());
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        List<TripMemberSnapshot> members = effectiveMembers(tripId);
        if ("SETTLED".equals(trip.getStatus())) {
            return response(trip, "SETTLED", members.size(), 0, true, trip.getUpdatedAt());
        }
        if (!"FINISHED".equals(trip.getStatus()) && !"ENDED".equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已结束行程可以结算");
        }

        int actualDistance = executionSettlementMapper.actualDistance(tripId, captainUserId);
        int totalTrackPoints = executionSettlementMapper.totalPoints(tripId, captainUserId);
        int validTrackPoints = executionSettlementMapper.validPoints(tripId, captainUserId);
        int coverageRate = totalTrackPoints == 0 ? 0
                : (int) Math.round(validTrackPoints * 100.0d / totalTrackPoints);
        String riskLevel = executionSettlementMapper.riskLevel(tripId);
        if (riskLevel == null) {
            riskLevel = "LOW";
        }
        int riskScore = java.util.Optional.ofNullable(
                executionSettlementMapper.riskScore(tripId)).orElse(0);
        int locationGapCount = java.util.Optional.ofNullable(
                executionSettlementMapper.locationGapCount(tripId)).orElse(0);
        java.math.BigDecimal endLng = trip.getEndLng() != null
                ? trip.getEndLng() : trip.getEndLongitude();
        java.math.BigDecimal endLat = trip.getEndLat() != null
                ? trip.getEndLat() : trip.getEndLatitude();
        boolean destinationArrived = endLng != null && endLat != null
                && executionSettlementMapper.hasDestinationArrival(
                tripId, captainUserId, endLng, endLat,
                destinationRadiusMeters, destinationMinPoints,
                destinationMinDurationSeconds) == 1
                && executionSettlementMapper.recentFatalAnomaliesAtDestination(
                tripId, captainUserId, endLng, endLat,
                destinationRadiusMeters, fatalArrivalCooldownSeconds) == 0;
        boolean automaticSettlement = totalTrackPoints > 0
                && destinationArrived
                && locationGapCount == 0
                && "LOW".equalsIgnoreCase(riskLevel);
        int pointsPerMember = automaticSettlement
                ? TripGrowthCalculator.points(actualDistance)
                : 0;
        String reviewReason = totalTrackPoints == 0
                ? "队长没有可用轨迹数据，需要人工审核"
                : !destinationArrived
                ? "队长没有进入终点1公里范围，需要人工审核"
                : locationGapCount > 0
                ? "队长轨迹存在中断，需要管理员酌情审核认可里程"
                : !"LOW".equalsIgnoreCase(riskLevel)
                ? "队长轨迹存在瞬移、模拟定位或极端速度等异常，需要人工审核"
                : "队长轨迹需要人工审核";

        String bizId = "trip-settlement:" + tripId;
        if (automaticSettlement) {
            for (TripMemberSnapshot member : members) {
                inviteFacade.completeFirstTeam(member.getUserId(), tripId, bizId);
                if (pointsPerMember > 0) {
                    growthFacade.grant(
                            member.getUserId(),
                            "TRIP_MILEAGE",
                            bizId,
                            pointsPerMember,
                            "完成行程，有效里程" + (actualDistance / 1000.0d) + "公里结算");
                }
            }
        }

        LocalDateTime settledAt = LocalDateTime.now();
        if (executionSettlementMapper.settlementExists(tripId) == 0) {
            executionSettlementMapper.insertSettlement(
                    SnowflakeIdGenerator.nextId(),
                    tripId,
                    actualDistance,
                    coverageRate,
                    automaticSettlement ? "QUALIFIED" : "REVIEW",
                    automaticSettlement ? "SETTLED" : "REVIEW_REQUIRED",
                    pointsPerMember,
                    automaticSettlement
                            ? "队长到达终点1公里范围且无中断和明显异常，按队长有效里程每5公里10成长值结算"
                            : reviewReason,
                    settledAt);
        }
        executionSettlementMapper.finishExecution(
                tripId, automaticSettlement ? "SETTLED" : "REVIEW_REQUIRED",
                actualDistance, settledAt);
        if (automaticSettlement) {
            executionSettlementMapper.updateTrackReview(
                    tripId, actualDistance, "LOW", "SETTLED",
                    "低风险轨迹自动审核通过", null, settledAt);
        }
        if (!automaticSettlement) {
            // 无论是轨迹中断、未到终点、无轨迹还是明显异常，都必须进入 Admin 人工审核队列。
            // 没有任何轨迹点时 driver-track 不会创建 trip_track_summary，这里补建一条空汇总，
            // 避免出现行程已经 REVIEW_REQUIRED 但后台列表查不到的情况。
            executionSettlementMapper.ensureTrackReviewSummary(
                    SnowflakeIdGenerator.nextId(), tripId, captainUserId, actualDistance, actualDistance,
                    totalTrackPoints, validTrackPoints, Math.max(0, totalTrackPoints - validTrackPoints),
                    locationGapCount, riskScore, riskLevel, reviewReason, settledAt);
            executionSettlementMapper.markTrackManualReview(tripId, reviewReason, settledAt);
            auditLogMapper.insert(SnowflakeIdGenerator.nextId(), tripId, userId, "SETTLEMENT_REVIEW",
                    "{\"status\":\"FINISHED\"}",
                    "{\"executionStatus\":\"REVIEW_REQUIRED\",\"coverageRate\":" + coverageRate
                            + ",\"destinationArrived\":" + destinationArrived
                            + ",\"locationGapCount\":" + locationGapCount
                            + ",\"riskLevel\":\"" + riskLevel + "\"}",
                    reviewReason + "，暂不发放成长值", settledAt);
            return response(
                    trip, "REVIEW_REQUIRED", members.size(), 0, false, settledAt);
        }
        // 兼容历史 ENDED 数据：先归一化到 FINISHED，再执行条件更新。
        if ("ENDED".equals(trip.getStatus())) {
            tripMapper.updateStatus(tripId, userId, "FINISHED", settledAt);
        }
        if (tripMapper.settleFinishedTrip(tripId, userId, settledAt) == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许结算");
        }
        auditLogMapper.insert(SnowflakeIdGenerator.nextId(), tripId, userId, "SETTLE",
                "{\"status\":\"FINISHED\"}",
                "{\"status\":\"SETTLED\",\"actualDistance\":" + actualDistance
                        + ",\"coverageRate\":" + coverageRate
                        + ",\"pointsPerMember\":" + pointsPerMember
                        + ",\"memberCount\":" + members.size() + "}",
                "按审核通过的有效GPS轨迹结算，每5公里发放10成长值", settledAt);
        clearCaches(userId, tripId);
        trip.setStatus("SETTLED");
        trip.setUpdatedAt(settledAt);
        return response(
                trip, "SETTLED", members.size(), pointsPerMember, false, settledAt);
    }

    @Override
    @Transactional
    public TripTrackReviewResponse reviewTrack(
            Long tripId, Long operatorId, TripTrackReviewRequest request) {
        Trip existing = tripMapper.findById(tripId);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        Trip trip = tripMapper.findOwnedForUpdate(tripId, existing.getUserId());
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在");
        }
        if (!List.of("FINISHED", "ENDED", "SETTLED").contains(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已结束行程可以审核轨迹结算");
        }
        TripTrackReviewSnapshot snapshot = executionSettlementMapper.findTrackReviewForUpdate(tripId);
        if (snapshot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该行程没有可审核的轨迹汇总");
        }
        List<TripMemberSnapshot> members = effectiveMembers(tripId);
        if (List.of("SETTLED", "REJECTED").contains(snapshot.getSettlementStatus())) {
            int approved = snapshot.getApprovedDistanceMeters() == null
                    ? 0 : snapshot.getApprovedDistanceMeters();
            int growth = "SETTLED".equals(snapshot.getSettlementStatus())
                    ? TripGrowthCalculator.points(approved) : 0;
            return reviewResponse(tripId, request.decision(), snapshot.getSettlementStatus(),
                    approved, growth, members.size(), true, snapshot.getReviewedAt());
        }

        String decision = request.decision().trim().toUpperCase(java.util.Locale.ROOT);
        int systemDistance = snapshot.getFilteredDistanceMeters() == null
                ? 0 : Math.max(0, snapshot.getFilteredDistanceMeters());
        boolean rejected = "REJECT_GROWTH".equals(decision);
        boolean modified = "APPROVE_MODIFIED_DISTANCE".equals(decision);
        if (modified && request.approvedDistanceMeters() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "修改里程后通过时必须填写审核后里程");
        }
        int approvedDistance = rejected ? 0 : modified
                ? Math.max(0, request.approvedDistanceMeters()) : systemDistance;
        int growthPerMember = rejected ? 0 : TripGrowthCalculator.points(approvedDistance);
        LocalDateTime reviewedAt = LocalDateTime.now();
        String finalStatus = rejected ? "REJECTED" : "SETTLED";
        String reviewedRiskLevel = "MARK_FALSE_POSITIVE".equals(decision)
                ? "LOW" : snapshot.getRiskLevel();

        if (!rejected) {
            String bizId = "trip-settlement:" + tripId;
            for (TripMemberSnapshot member : members) {
                inviteFacade.completeFirstTeam(member.getUserId(), tripId, bizId);
                if (growthPerMember > 0) {
                    growthFacade.grant(
                            member.getUserId(), "TRIP_MILEAGE", bizId, growthPerMember,
                            "管理员审核通过行程轨迹，有效里程"
                                    + (approvedDistance / 1000.0d) + "公里");
                }
            }
        }

        executionSettlementMapper.updateTrackReview(
                tripId, approvedDistance, reviewedRiskLevel, finalStatus,
                request.reason().trim(), operatorId, reviewedAt);
        int coverage = snapshot.getTotalPointCount() == null || snapshot.getTotalPointCount() == 0
                ? 0 : (int) Math.round((snapshot.getValidPointCount() == null ? 0
                : snapshot.getValidPointCount()) * 100.0d / snapshot.getTotalPointCount());
        int updated = executionSettlementMapper.updateMileageSettlementReview(
                tripId, approvedDistance, rejected ? "REJECTED" : "MANUAL_APPROVED",
                finalStatus, growthPerMember, request.reason().trim(), reviewedAt);
        if (updated == 0) {
            executionSettlementMapper.insertSettlement(
                    SnowflakeIdGenerator.nextId(), tripId, approvedDistance, coverage,
                    rejected ? "REJECTED" : "MANUAL_APPROVED", finalStatus,
                    growthPerMember, request.reason().trim(), reviewedAt);
        }
        executionSettlementMapper.reviewExecution(
                tripId, rejected ? "REVIEW_REJECTED" : "SETTLED", approvedDistance, reviewedAt);
        if (!rejected) {
            executionSettlementMapper.settleTripByAdmin(tripId, reviewedAt);
        }
        auditLogMapper.insert(
                SnowflakeIdGenerator.nextId(), tripId, operatorId, "TRACK_REVIEW",
                "{\"riskLevel\":\"" + snapshot.getRiskLevel()
                        + "\",\"filteredDistance\":" + systemDistance + "}",
                "{\"decision\":\"" + decision + "\",\"approvedDistance\":"
                        + approvedDistance + ",\"growthPerMember\":" + growthPerMember
                        + ",\"requestId\":\"" + jsonEscape(request.requestId()) + "\"}",
                request.reason().trim(), reviewedAt);
        clearCaches(trip.getUserId(), tripId);
        return reviewResponse(tripId, decision, finalStatus, approvedDistance,
                growthPerMember, members.size(), false, reviewedAt);
    }

    private TripTrackReviewResponse reviewResponse(
            Long tripId, String decision, String status, int approvedDistance,
            int growthPerMember, int memberCount, boolean duplicate, LocalDateTime reviewedAt) {
        return new TripTrackReviewResponse(
                String.valueOf(tripId), decision, status, approvedDistance, growthPerMember,
                memberCount, growthPerMember * memberCount, duplicate,
                reviewedAt == null ? "" : reviewedAt.format(TIME_FORMATTER));
    }

    private List<TripMemberSnapshot> effectiveMembers(Long tripId) {
        List<Long> eligibleUserIds = executionSettlementMapper.eligibleMemberIds(tripId);
        return memberMapper.findSettlementMembersByTripId(tripId).stream()
                .filter(member -> eligibleUserIds.isEmpty()
                        || eligibleUserIds.contains(member.getUserId()))
                .toList();
    }

    private TripSettlementResponse response(
            Trip trip, String status, int memberCount, int pointsPerMember,
            boolean duplicate, LocalDateTime settledAt) {
        return new TripSettlementResponse(String.valueOf(trip.getId()), status, memberCount,
                pointsPerMember, memberCount * pointsPerMember, duplicate,
                settledAt == null ? "" : settledAt.format(TIME_FORMATTER));
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void clearCaches(Long userId, Long tripId) {
        redisTemplate.delete("trip:cache:detail:" + tripId);
        redisTemplate.delete("trip:cache:mine:" + userId + ":active");
        redisTemplate.delete("trip:cache:mine:" + userId + ":history");
        redisTemplate.delete("trip:cache:public:list:20");
        redisTemplate.delete("trip:cache:public:list:50");
    }
}
