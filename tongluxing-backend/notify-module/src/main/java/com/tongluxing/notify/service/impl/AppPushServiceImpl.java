package com.tongluxing.notify.service.impl;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.notify.dto.RegisterPushDeviceRequest;
import com.tongluxing.notify.entity.AppPushDevice;
import com.tongluxing.notify.entity.AppPushTask;
import com.tongluxing.notify.mapper.AppPushDeviceMapper;
import com.tongluxing.notify.mapper.AppPushTaskMapper;
import com.tongluxing.notify.service.AppPushService;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/** APP 系统推送设备登记与任务入队实现。 */
@Service
@RequiredArgsConstructor
public class AppPushServiceImpl implements AppPushService {

    private final CurrentUserContext currentUserContext;
    private final AppPushDeviceMapper deviceMapper;
    private final AppPushTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void registerCurrentDevice(RegisterPushDeviceRequest request) {
        Long userId = currentUserContext.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        AppPushDevice device = new AppPushDevice();
        device.setId(SnowflakeIdGenerator.nextId());
        device.setUserId(userId);
        device.setDeviceId(request.deviceId().trim());
        device.setPlatform(request.platform().trim().toUpperCase());
        device.setVendor(request.vendor().trim().toUpperCase());
        device.setPushToken(request.pushToken().trim());
        device.setAppVersion(StringUtils.hasText(request.appVersion()) ? request.appVersion().trim() : null);
        device.setEnabled(1);
        device.setLastSeenAt(now);
        device.setCreatedAt(now);
        device.setUpdatedAt(now);
        device.setDeleted(0);
        deviceMapper.upsert(device);
    }

    @Override
    @Transactional
    public void disableCurrentDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备 ID 不能为空");
        }
        deviceMapper.disable(currentUserContext.requireUserId(), deviceId.trim(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void enqueue(Long userId, String eventType, String title, String content,
                        String targetType, String targetId, String idempotencyKey) {
        if (userId == null || !StringUtils.hasText(idempotencyKey)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AppPushTask task = new AppPushTask();
        task.setId(SnowflakeIdGenerator.nextId());
        task.setUserId(userId);
        task.setEventType(value(eventType, "SYSTEM"));
        task.setTitle(value(title, "同路行提醒"));
        task.setContent(value(content, "你有一条新的行程提醒"));
        task.setPayloadJson(toJson(Map.of(
                "targetType", targetType == null ? "" : targetType,
                "targetId", targetId == null ? "" : targetId,
                "eventType", value(eventType, "SYSTEM")
        )));
        task.setIdempotencyKey(idempotencyKey.trim());
        task.setDeliveryStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryAt(now);
        task.setLastError("");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insertIgnore(task);
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "推送参数序列化失败");
        }
    }
}
