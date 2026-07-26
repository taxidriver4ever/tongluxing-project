package com.tongluxing.trip.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripMemberSnapshot;
import com.tongluxing.trip.mapper.TripAuditLogMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
import com.tongluxing.trip.mapper.TripExecutionSettlementMapper;
import com.tongluxing.trip.service.TripSettlementService;
import com.tongluxing.trip.vo.TripSettlementResponse;
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
    private static final int GROWTH_DISTANCE_UNIT_METERS = 5_000;
    private static final int GROWTH_PER_DISTANCE_UNIT = 10;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CurrentUserContext currentUserContext;
    private final TripMapper tripMapper;
    private final TripMemberSnapshotMapper memberMapper;
    private final TripExecutionSettlementMapper executionSettlementMapper;
    private final TripAuditLogMapper auditLogMapper;
    private final InviteFacade inviteFacade;
    private final GrowthFacade growthFacade;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public TripSettlementResponse settle(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        Trip trip = tripMapper.findOwnedForUpdate(tripId, userId);
        if (trip == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程不存在或不属于当前用户");
        }
        List<TripMemberSnapshot> members = effectiveMembers(tripId);
        if ("SETTLED".equals(trip.getStatus())) {
            return response(trip, "SETTLED", members.size(), 0, true, trip.getUpdatedAt());
        }
        if (!"FINISHED".equals(trip.getStatus()) && !"ENDED".equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已结束行程可以结算");
        }

        int actualDistance = executionSettlementMapper.actualDistance(tripId, trip.getUserId());
        int totalTrackPoints = executionSettlementMapper.totalPoints(tripId, trip.getUserId());
        int validTrackPoints = executionSettlementMapper.validPoints(tripId, trip.getUserId());
        int coverageRate = totalTrackPoints == 0 ? 0
                : (int) Math.round(validTrackPoints * 100.0d / totalTrackPoints);
        java.math.BigDecimal endLng = trip.getEndLng() != null
                ? trip.getEndLng() : trip.getEndLongitude();
        java.math.BigDecimal endLat = trip.getEndLat() != null
                ? trip.getEndLat() : trip.getEndLatitude();
        boolean destinationArrived = endLng != null && endLat != null
                && executionSettlementMapper.hasDestinationArrival(
                tripId, trip.getUserId(), endLng, endLat) == 1;
        int requiredWaypointCount = executionSettlementMapper.requiredWaypointCount(tripId);
        int arrivedRequiredWaypointCount =
                executionSettlementMapper.arrivedRequiredWaypointCount(tripId, trip.getUserId());
        boolean routeCompleted = destinationArrived
                && arrivedRequiredWaypointCount >= requiredWaypointCount;
        boolean automaticSettlement = totalTrackPoints > 0 && coverageRate >= 90 && routeCompleted;
        int pointsPerMember = automaticSettlement
                ? (actualDistance / GROWTH_DISTANCE_UNIT_METERS) * GROWTH_PER_DISTANCE_UNIT
                : 0;

        String bizId = "trip-settlement:" + tripId;
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

        LocalDateTime settledAt = LocalDateTime.now();
        if (executionSettlementMapper.settlementExists(tripId) == 0) {
            executionSettlementMapper.insertSettlement(
                    SnowflakeIdGenerator.nextId(),
                    tripId,
                    actualDistance,
                    coverageRate,
                    coverageRate >= 90 ? "QUALIFIED" : coverageRate >= 70 ? "REVIEW" : "UNQUALIFIED",
                    automaticSettlement ? "SETTLED" : "REVIEW_REQUIRED",
                    pointsPerMember,
                    automaticSettlement ? "轨迹与节点证据合格，按每5公里10成长值结算"
                            : !routeCompleted ? "终点或必达途经点缺少100米内持续30秒的到达证据"
                            : "轨迹覆盖率不足90%，需要人工复核",
                    settledAt);
        }
        executionSettlementMapper.finishExecution(
                tripId, automaticSettlement ? "SETTLED" : "REVIEW_REQUIRED",
                actualDistance, settledAt);
        if (!automaticSettlement) {
            auditLogMapper.insert(SnowflakeIdGenerator.nextId(), tripId, userId, "SETTLEMENT_REVIEW",
                    "{\"status\":\"FINISHED\"}",
                    "{\"executionStatus\":\"REVIEW_REQUIRED\",\"coverageRate\":" + coverageRate
                            + ",\"routeCompleted\":" + routeCompleted + "}",
                    "轨迹质量或节点到达证据不足，转人工复核且暂不发放成长值", settledAt);
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
                "按实际GPS轨迹结算，每5公里发放10成长值", settledAt);
        clearCaches(userId, tripId);
        trip.setStatus("SETTLED");
        trip.setUpdatedAt(settledAt);
        return response(
                trip, "SETTLED", members.size(), pointsPerMember, false, settledAt);
    }

    private List<TripMemberSnapshot> effectiveMembers(Long tripId) {
        List<Long> eligibleUserIds = executionSettlementMapper.eligibleMemberIds(tripId);
        return memberMapper.findByTripId(tripId).stream()
                .filter(member -> List.of("OWNER", "APPROVED").contains(member.getJoinStatus()))
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

    private void clearCaches(Long userId, Long tripId) {
        redisTemplate.delete("trip:cache:detail:" + tripId);
        redisTemplate.delete("trip:cache:mine:" + userId + ":active");
        redisTemplate.delete("trip:cache:mine:" + userId + ":history");
        redisTemplate.delete("trip:cache:public:list:20");
        redisTemplate.delete("trip:cache:public:list:50");
    }
}
