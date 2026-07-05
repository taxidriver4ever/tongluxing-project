package com.tongluxing.groupbuy.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.groupbuy.dto.CreateGroupbuyRequest;
import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.entity.GroupbuyActivity;
import com.tongluxing.groupbuy.entity.GroupbuyParticipant;
import com.tongluxing.groupbuy.integration.GroupbuyMerchantProductPort;
import com.tongluxing.groupbuy.mapper.GroupbuyActivityMapper;
import com.tongluxing.groupbuy.mapper.GroupbuyParticipantMapper;
import com.tongluxing.groupbuy.service.GroupbuyService;
import com.tongluxing.groupbuy.vo.GroupbuyActivityVO;
import com.tongluxing.groupbuy.vo.GroupbuyParticipantVO;
import com.tongluxing.groupbuy.vo.PageResult;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;
/**
 * GroupbuyServiceImpl 业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class GroupbuyServiceImpl implements GroupbuyService {
    private final CurrentUserContext currentUserContext;
    private final GroupbuyMerchantProductPort merchantProductPort;
    private final GroupbuyActivityMapper activityMapper;
    private final GroupbuyParticipantMapper participantMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public GroupbuyActivityVO create(CreateGroupbuyRequest request) {
        Long userId = currentUserContext.requireUserId();
        String idemKey = "groupbuy:idem:create:%s".formatted(request.requestId());
        GroupbuyActivityVO cached = readJson(idemKey, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        LocalDateTime now = LocalDateTime.now();
        GroupbuyMerchantProductPort.MerchantProductSnapshot snapshot =
                merchantProductPort.getSnapshot(request.productId());
        int targetPeople = request.targetPeople() == null ? snapshot.targetPeople() : request.targetPeople();
        int validHours = request.validHours() == null ? snapshot.validHours() : request.validHours();
        merchantProductPort.decreaseStock(request.productId(), 1, "groupbuy:create:" + request.requestId());

        GroupbuyActivity activity = new GroupbuyActivity();
        activity.setId(SnowflakeIdGenerator.nextId());
        activity.setMerchantId(snapshot.merchantId());
        activity.setProductId(request.productId());
        activity.setInitiatorUserId(userId);
        activity.setTargetPeople(targetPeople);
        activity.setCurrentPeople(0);
        activity.setGroupPrice(snapshot.groupPrice());
        activity.setLadderPriceJson(StringUtils.hasText(snapshot.ladderPriceJson()) ? snapshot.ladderPriceJson() : "[]");
        activity.setActivityStatus("ONGOING");
        activity.setStartAt(now);
        activity.setExpireAt(now.plusHours(validHours));
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        activity.setDeleted(0);
        activityMapper.insert(activity);
        GroupbuyActivityVO result = toVO(activityMapper.findById(activity.getId()));
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public PageResult<GroupbuyActivityVO> list(String status, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        List<GroupbuyActivityVO> records = activityMapper.list(normalizedStatus, offset, normalizedSize)
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, activityMapper.count(normalizedStatus), normalizedPage, normalizedSize);
    }

    @Override
    public GroupbuyActivityVO detail(Long activityId) {
        String key = "groupbuy:cache:activity:%d".formatted(activityId);
        GroupbuyActivityVO cached = readJson(key, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        GroupbuyActivityVO result = toVO(requireActivity(activityId));
        writeJson(key, result, Duration.ofMinutes(5));
        return result;
    }

    @Override
    @Transactional
    public GroupbuyActivityVO addPaidParticipant(Long activityId, PaidParticipantRequest request) {
        String idemKey = "groupbuy:idem:paid-participant:%s".formatted(request.requestId());
        GroupbuyActivityVO cached = readJson(idemKey, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        GroupbuyActivity activity = requireActivity(activityId);
        if (!"ONGOING".equals(activity.getActivityStatus())) {
            return toVO(activity);
        }
        if (participantMapper.findByActivityAndUser(activityId, request.userId()) == null) {
            LocalDateTime now = LocalDateTime.now();
            GroupbuyParticipant participant = new GroupbuyParticipant();
            participant.setId(SnowflakeIdGenerator.nextId());
            participant.setActivityId(activityId);
            participant.setOrderId(request.orderId());
            participant.setUserId(request.userId());
            participant.setParticipantStatus("PAID");
            participant.setJoinedAt(now);
            participant.setPaidAt(request.paidAt() == null ? now : request.paidAt());
            participant.setCreatedAt(now);
            participant.setUpdatedAt(now);
            participant.setDeleted(0);
            participantMapper.insert(participant);
            activityMapper.increasePeople(activityId, now);
            activityMapper.markSuccessIfReached(activityId, now);
            redis.delete("groupbuy:cache:activity:%d".formatted(activityId));
        }
        GroupbuyActivityVO result = toVO(requireActivity(activityId));
        writeJson(idemKey, result, Duration.ofDays(7));
        return result;
    }

    @Override
    @Transactional
    public GroupbuyActivityVO expire(Long activityId) {
        LocalDateTime now = LocalDateTime.now();
        activityMapper.markFailedIfExpired(activityId, now);
        redis.delete("groupbuy:cache:activity:%d".formatted(activityId));
        return toVO(requireActivity(activityId));
    }

    /**
     * 消费运营后台干预任务，真实推进拼团状态。
     */
    @Override
    @Transactional
    public GroupbuyActivityVO applyAdminIntervention(Long activityId, String action, String reason, String requestId) {
        String idemKey = "groupbuy:idem:admin-intervention:%s".formatted(requestId);
        GroupbuyActivityVO cached = readJson(idemKey, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        String normalizedAction = action == null ? "" : action.trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        if ("FORCE_SUCCESS".equals(normalizedAction)) {
            activityMapper.forceSuccess(activityId, now);
        } else if ("FORCE_FAILED".equals(normalizedAction)) {
            activityMapper.forceEnd(activityId, "FAILED", now);
        } else if ("OFFLINE".equals(normalizedAction)) {
            activityMapper.forceEnd(activityId, "OFFLINE", now);
        } else {
            throw new BusinessException("拼团干预动作不支持");
        }
        redis.delete("groupbuy:cache:activity:%d".formatted(activityId));
        GroupbuyActivityVO result = toVO(requireActivity(activityId));
        writeJson(idemKey, result, Duration.ofDays(7));
        return result;
    }

    private GroupbuyActivity requireActivity(Long activityId) {
        GroupbuyActivity activity = activityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拼团活动不存在");
        }
        return activity;
    }

    private GroupbuyActivityVO toVO(GroupbuyActivity activity) {
        List<GroupbuyParticipantVO> participants = participantMapper.findByActivityId(activity.getId())
                .stream()
                .map(p -> new GroupbuyParticipantVO(p.getId(), p.getActivityId(), p.getOrderId(), p.getUserId(),
                        p.getParticipantStatus(), p.getJoinedAt(), p.getPaidAt(), p.getRefundedAt()))
                .toList();
        return new GroupbuyActivityVO(activity.getId(), activity.getMerchantId(), activity.getProductId(),
                activity.getInitiatorUserId(), activity.getTargetPeople(), activity.getCurrentPeople(),
                activity.getGroupPrice(), activity.getActivityStatus(), activity.getStartAt(), activity.getExpireAt(),
                activity.getSuccessAt(), activity.getFailedAt(), participants);
    }

    private <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实数据。
        }
    }
}
