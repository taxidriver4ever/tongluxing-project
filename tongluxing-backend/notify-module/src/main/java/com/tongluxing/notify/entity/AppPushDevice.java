package com.tongluxing.notify.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 系统推送设备实体。 */
@Data
public class AppPushDevice {
    private Long id;
    private Long userId;
    private String deviceId;
    private String platform;
    private String vendor;
    private String pushToken;
    private String appVersion;
    private Integer enabled;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
