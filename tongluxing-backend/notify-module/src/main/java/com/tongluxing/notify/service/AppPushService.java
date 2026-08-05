package com.tongluxing.notify.service;

import com.tongluxing.notify.dto.RegisterPushDeviceRequest;

/** APP 系统推送服务。 */
public interface AppPushService {
    void registerCurrentDevice(RegisterPushDeviceRequest request);
    void disableCurrentDevice(String deviceId);
    void enqueue(Long userId, String eventType, String title, String content,
                 String targetType, String targetId, String idempotencyKey);
}
