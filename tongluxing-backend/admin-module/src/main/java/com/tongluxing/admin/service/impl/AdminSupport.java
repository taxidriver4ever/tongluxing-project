package com.tongluxing.admin.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.admin.entity.AdminAuditLog;
import com.tongluxing.admin.entity.AdminCompensationTask;
import com.tongluxing.admin.mapper.AdminAuditLogMapper;
import com.tongluxing.admin.mapper.AdminCompensationTaskMapper;
import com.tongluxing.common.utils.SnowflakeIdGenerator;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台通用支撑组件。
 *
 * <p>封装审计日志写入、补偿任务创建、Redis JSON 缓存和字符串标准化等横切能力。
 * 该类为包内组件，仅供 admin-module 的 service 实现复用。</p>
 */
@Component
@RequiredArgsConstructor
class AdminSupport {

    /** 通用操作成功状态。 */
    static final String SUCCESS = "SUCCESS";
    /** 通用操作失败状态。 */
    static final String FAILED = "FAILED";
    /** 补偿任务待处理状态。 */
    static final String PENDING = "PENDING";

    /** 后台审计日志 Mapper。 */
    private final AdminAuditLogMapper auditLogMapper;
    /** 补偿任务 Mapper。 */
    private final AdminCompensationTaskMapper compensationTaskMapper;
    /** Redis 操作模板，用于缓存和幂等结果保存。 */
    private final StringRedisTemplate redis;
    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    /** 写入后台操作审计日志。 */
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

    /** 创建跨模块补偿任务，后续由任务处理器或业务模块消费。 */
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

    /** 从 Redis 读取 JSON 缓存；读取或反序列化失败时返回空，避免影响主流程。 */
    <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 写入 JSON 缓存；Redis 异常不影响 MySQL 事实数据。 */
    void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实写入。
        }
    }

    /** 删除指定 Redis key；失败时忽略，避免缓存清理影响业务写入。 */
    void deleteRedis(String key) {
        try {
            redis.delete(key);
        } catch (Exception ignored) {
            // Redis 删除失败不影响主流程。
        }
    }

    /** 统一将枚举类输入标准化为大写并去除首尾空白。 */
    String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
