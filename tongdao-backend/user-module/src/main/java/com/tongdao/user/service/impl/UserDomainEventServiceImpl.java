package com.tongdao.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.user.mapper.UserDomainMapper;
import com.tongdao.user.service.UserDomainEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDomainEventServiceImpl implements UserDomainEventService {

    private final UserDomainMapper mapper;
    private final StringRedisTemplate redis;

    @Value("${tongdao.user.growth.team-trip-completed-points:100}")
    private int completedPoints;

    @Override
    @Transactional
    public void handleTeamTripCompleted(Long tripId, Long userId, String bizId) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> account = mapper.findGrowthForUpdate(userId);
        if (account == null) {
            try { mapper.insertGrowth(SnowflakeIdGenerator.nextId(), userId, now); }
            catch (DuplicateKeyException ignored) { }
            account = mapper.findGrowthForUpdate(userId);
        }

        int oldPoints = number(account, "totalPoints");
        int newPoints = oldPoints + completedPoints;
        String eventBizId = bizId + ":" + userId;
        try {
            mapper.insertGrowthLog(SnowflakeIdGenerator.nextId(), userId, "TEAM_TRIP_COMPLETED", eventBizId,
                    completedPoints, newPoints, "完成有效同行", now);
        } catch (DuplicateKeyException ignored) {
            return;
        }

        String levelCode = mapper.findLevelCode(newPoints);
        if (levelCode == null) levelCode = "LV1";
        if (mapper.updateGrowth(longValue(account, "id"), newPoints, levelCode, number(account, "version"), now) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "成长账户并发更新失败");
        }

        int eventCount = mapper.countGrowthEvents(userId, "TEAM_TRIP_COMPLETED");
        List<Long> badges = mapper.findEligibleBadgeIds("TEAM_TRIP_COMPLETED", eventCount);
        for (Long badgeId : badges) {
            mapper.insertUserBadge(SnowflakeIdGenerator.nextId(), userId, badgeId, eventBizId, now);
        }
        Long inviterId = mapper.findInviterIdByInvitee(userId);
        mapper.markInviteRelationValid(userId, now);
        clearCaches(userId);
        if (inviterId != null) clearCaches(inviterId);
    }

    private void clearCaches(Long userId) {
        try {
            redis.delete(List.of(
                    "user:cache:growth:" + userId,
                    "user:cache:badges:" + userId,
                    "user:cache:invite-summary:" + userId,
                    "user:cache:dashboard:" + userId
            ));
        } catch (RuntimeException ignored) { }
    }

    private int number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }
}
