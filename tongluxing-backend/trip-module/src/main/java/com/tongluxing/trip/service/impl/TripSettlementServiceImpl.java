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
import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.invite.integration.InviteFacade;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripMemberSnapshot;
import com.tongluxing.trip.mapper.TripAuditLogMapper;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
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
    private static final int COMPLETION_POINTS = 100;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CurrentUserContext currentUserContext;
    private final TripMapper tripMapper;
    private final TripMemberSnapshotMapper memberMapper;
    private final TripAuditLogMapper auditLogMapper;
    private final GrowthFacade growthFacade;
    private final InviteFacade inviteFacade;
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
            return response(trip, members.size(), true, trip.getUpdatedAt());
        }
        if (!"FINISHED".equals(trip.getStatus()) && !"ENDED".equals(trip.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已结束行程可以结算");
        }

        String bizId = "trip-settlement:" + tripId;
        for (TripMemberSnapshot member : members) {
            growthFacade.grant(member.getUserId(), "TEAM_TRIP_COMPLETED", bizId,
                    COMPLETION_POINTS, "完成行程「" + trip.getTitle() + "」成长值结算");
            inviteFacade.completeFirstTeam(member.getUserId(), tripId, bizId);
        }

        LocalDateTime settledAt = LocalDateTime.now();
        // 兼容历史 ENDED 数据：先归一化到 FINISHED，再执行条件更新。
        if ("ENDED".equals(trip.getStatus())) {
            tripMapper.updateStatus(tripId, userId, "FINISHED", settledAt);
        }
        if (tripMapper.settleFinishedTrip(tripId, userId, settledAt) == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不允许结算");
        }
        auditLogMapper.insert(SnowflakeIdGenerator.nextId(), tripId, userId, "SETTLE",
                "{\"status\":\"FINISHED\"}",
                "{\"status\":\"SETTLED\",\"pointsPerMember\":" + COMPLETION_POINTS
                        + ",\"memberCount\":" + members.size() + "}",
                "完成成长值结算", settledAt);
        clearCaches(userId, tripId);
        trip.setStatus("SETTLED");
        trip.setUpdatedAt(settledAt);
        return response(trip, members.size(), false, settledAt);
    }

    private List<TripMemberSnapshot> effectiveMembers(Long tripId) {
        return memberMapper.findByTripId(tripId).stream()
                .filter(member -> List.of("OWNER", "APPROVED").contains(member.getJoinStatus()))
                .toList();
    }

    private TripSettlementResponse response(Trip trip, int memberCount, boolean duplicate, LocalDateTime settledAt) {
        return new TripSettlementResponse(String.valueOf(trip.getId()), "SETTLED", memberCount,
                COMPLETION_POINTS, memberCount * COMPLETION_POINTS, duplicate,
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
