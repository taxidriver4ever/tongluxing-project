package com.tongluxing.notify.service.impl;

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
import com.tongluxing.notify.dto.CreateNotificationEventRequest;
import com.tongluxing.notify.dto.NotifyMessageQueryDTO;
import com.tongluxing.notify.mapper.NotifyMessageMapper;
import com.tongluxing.notify.service.NotificationService;
import com.tongluxing.notify.service.AppPushService;
import com.tongluxing.notify.vo.NotificationVO;
import com.tongluxing.notify.vo.PageResult;
import com.tongluxing.notify.vo.UnreadCountVO;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 通知业务服务实现。
 *
 * <p>站内通知落库作为事实来源，Redis 只承担幂等和未读数缓存。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String RECEIVER_USER = "USER";
    private static final String IDEM_EVENT_KEY = "notify:idem:event:%s";
    private static final String UNREAD_COUNT_KEY = "notify:unread-count:%s:%d";
    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUserContext currentUserContext;
    private final NotifyMessageMapper messageMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppPushService appPushService;

    @Override
    @Transactional
    public NotificationVO createEvent(CreateNotificationEventRequest request) {
        String requestId = request.requestId().trim();
        NotificationVO cached = readJson(IDEM_EVENT_KEY.formatted(requestId), NotificationVO.class);
        if (cached != null) {
            return cached;
        }
        NotifyMessageQueryDTO existed = messageMapper.findByRequestId(requestId);
        if (existed != null) {
            NotificationVO result = toVO(existed);
            writeJson(IDEM_EVENT_KEY.formatted(requestId), result, Duration.ofHours(24));
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        long id = SnowflakeIdGenerator.nextId();
        messageMapper.insertMessage(
                id,
                normalize(request.receiverType()),
                request.receiverId(),
                valueOrDefault(request.scene(), "SYSTEM"),
                normalize(request.eventType()),
                request.title().trim(),
                request.content().trim(),
                trimToEmpty(request.targetType()),
                trimToEmpty(request.targetId()),
                requestId,
                now);
        // P0：站内记录只作为审计/历史，关键提醒同时进入 APP 系统推送任务。
        if (RECEIVER_USER.equals(normalize(request.receiverType()))) {
            appPushService.enqueue(
                    request.receiverId(), normalize(request.eventType()), request.title(), request.content(),
                    request.targetType(), request.targetId(), "notify:" + requestId);
        }
        deleteUnreadCache(normalize(request.receiverType()), request.receiverId());
        NotifyMessageQueryDTO created = messageMapper.findByRequestId(requestId);
        NotificationVO result = toVO(created);
        writeJson(IDEM_EVENT_KEY.formatted(requestId), result, Duration.ofHours(24));
        return result;
    }

    @Override
    public PageResult<NotificationVO> listCurrentUser(String scene, Boolean unreadOnly, int page, int size) {
        Long userId = currentUserContext.requireUserId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        String normalizedScene = trimToNull(scene);
        List<NotificationVO> records = messageMapper.listMessages(
                RECEIVER_USER, userId, normalizedScene, onlyUnread, offset, normalizedSize)
                .stream()
                .map(this::toVO)
                .toList();
        long total = messageMapper.countMessages(RECEIVER_USER, userId, normalizedScene, onlyUnread);
        return new PageResult<>(records, total, normalizedPage, normalizedSize);
    }

    @Override
    public UnreadCountVO countCurrentUserUnread() {
        Long userId = currentUserContext.requireUserId();
        String key = UNREAD_COUNT_KEY.formatted(RECEIVER_USER, userId);
        String cached = readString(key);
        if (cached != null) {
            return new UnreadCountVO(Long.parseLong(cached));
        }
        long count = messageMapper.countUnread(RECEIVER_USER, userId);
        writeString(key, String.valueOf(count), Duration.ofMinutes(10));
        return new UnreadCountVO(count);
    }

    @Override
    @Transactional
    public void markCurrentUserRead(Long notificationId) {
        Long userId = currentUserContext.requireUserId();
        int changed = messageMapper.markRead(notificationId, RECEIVER_USER, userId, LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        deleteUnreadCache(RECEIVER_USER, userId);
    }

    @Override
    @Transactional
    public void markCurrentUserAllRead() {
        Long userId = currentUserContext.requireUserId();
        messageMapper.markAllRead(RECEIVER_USER, userId, LocalDateTime.now());
        deleteUnreadCache(RECEIVER_USER, userId);
    }

    private NotificationVO toVO(NotifyMessageQueryDTO row) {
        if (row == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "通知创建后读取失败");
        }
        return new NotificationVO(
                row.getId(),
                row.getReceiverType(),
                row.getReceiverId(),
                row.getScene(),
                row.getEventType(),
                row.getTitle(),
                row.getContent(),
                row.getTargetType(),
                row.getTargetId(),
                row.getReadStatus(),
                row.getReadAt(),
                row.getCreatedAt());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : defaultValue;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private void deleteUnreadCache(String receiverType, Long receiverId) {
        try {
            redis.delete(UNREAD_COUNT_KEY.formatted(receiverType, receiverId));
        } catch (Exception ignored) {
            // 缓存失败不影响站内通知事实数据。
        }
    }

    private String readString(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeString(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
            // 缓存失败不影响主流程。
        }
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
            // Redis 幂等缓存失败时仍有 MySQL requestId 兜底。
        }
    }
}
