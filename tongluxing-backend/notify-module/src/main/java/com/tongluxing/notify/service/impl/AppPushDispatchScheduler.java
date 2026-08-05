package com.tongluxing.notify.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tongluxing.notify.entity.AppPushDevice;
import com.tongluxing.notify.entity.AppPushTask;
import com.tongluxing.notify.mapper.AppPushDeviceMapper;
import com.tongluxing.notify.mapper.AppPushTaskMapper;
import com.tongluxing.notify.service.PushGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 扫描待发送系统推送，失败后按指数级分钟数重试。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppPushDispatchScheduler {

    private final AppPushTaskMapper taskMapper;
    private final AppPushDeviceMapper deviceMapper;
    private final PushGateway pushGateway;

    @Scheduled(fixedDelayString = "${app.push.dispatch-delay-ms:5000}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        for (AppPushTask task : taskMapper.findDue(now, 100)) {
            dispatchOne(task);
        }
    }

    private void dispatchOne(AppPushTask task) {
        List<AppPushDevice> devices = deviceMapper.findEnabledByUserId(task.getUserId());
        if (devices.isEmpty()) {
            taskMapper.markFailed(task.getId(), "SKIPPED", null,
                    "用户尚未登记系统推送设备", LocalDateTime.now());
            return;
        }
        try {
            // 同一用户允许多设备登录，每个启用设备都接收关键业务提醒。
            for (AppPushDevice device : devices) {
                pushGateway.send(device, task);
            }
            taskMapper.markSent(task.getId(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
            long delayMinutes = Math.min(60, 1L << Math.min(retry, 6));
            taskMapper.markFailed(task.getId(), "FAILED", LocalDateTime.now().plusMinutes(delayMinutes),
                    truncate(ex.getMessage()), LocalDateTime.now());
            log.warn("APP 推送失败，taskId={}, retry={}", task.getId(), retry + 1, ex);
        }
    }

    private String truncate(String value) {
        if (value == null) return "unknown";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
