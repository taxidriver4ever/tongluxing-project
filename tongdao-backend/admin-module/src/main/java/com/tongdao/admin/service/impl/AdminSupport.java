package com.tongdao.admin.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.admin.entity.AdminAuditLog;
import com.tongdao.admin.entity.AdminCompensationTask;
import com.tongdao.admin.mapper.AdminAuditLogMapper;
import com.tongdao.admin.mapper.AdminCompensationTaskMapper;
import com.tongdao.common.utils.SnowflakeIdGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AdminSupport {
    static final String SUCCESS = "SUCCESS";
    static final String FAILED = "FAILED";
    static final String PENDING = "PENDING";

    private final AdminAuditLogMapper auditLogMapper;
    private final AdminCompensationTaskMapper compensationTaskMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    AdminAuditLog audit(String actionType, String targetModule, String targetType, String targetId,
                        String requestId, Long operatorId, String reason, String result,
                        String beforeSnapshot, String afterSnapshot) {
        AdminAuditLog log = new AdminAuditLog();
        log.setId(SnowflakeIdGenerator.nextId());
        log.setOperatorId(operatorId);
        log.setOperatorName("operator-" + operatorId);
        log.setActionType(normalize(actionType));
        log.setTargetModule(targetModule);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setRequestId(requestId);
        log.setBeforeSnapshot(beforeSnapshot);
        log.setAfterSnapshot(afterSnapshot);
        log.setOperationReason(reason);
        log.setOperationResult(result);
        log.setIp("");
        log.setUserAgent("");
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
        return log;
    }

    void compensation(String bizType, String bizId, String requestId, String targetModule, String payload) {
        LocalDateTime now = LocalDateTime.now();
        AdminCompensationTask task = new AdminCompensationTask();
        task.setId(SnowflakeIdGenerator.nextId());
        task.setBizType(normalize(bizType));
        task.setBizId(bizId);
        task.setIdempotentKey("admin:%s:%s".formatted(requestId, bizType));
        task.setTargetModule(targetModule);
        task.setRequestPayload(payload);
        task.setTaskStatus(PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(now.plusMinutes(1));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        compensationTaskMapper.insert(task);
    }

    <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实写入。
        }
    }

    void deleteRedis(String key) {
        try {
            redis.delete(key);
        } catch (Exception ignored) {
            // Redis 删除失败不影响主流程。
        }
    }

    String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
