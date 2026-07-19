package com.tongluxing.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** 当前联调阶段的 Web Admin 固定账号与安全策略配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "admin.auth")
public class AdminAuthProperties {
    private String username = "admin";
    private String password = "Admin@123456";
    private Long operatorId = 10001L;
    private String displayName = "运营管理员";
    private Integer sessionExpireSeconds = 7200;
    private Integer maxFailures = 5;
    private Integer failureWindowSeconds = 3600;
}
