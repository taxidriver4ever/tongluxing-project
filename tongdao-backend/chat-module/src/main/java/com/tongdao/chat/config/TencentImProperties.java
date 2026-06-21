package com.tongdao.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "tencent.im")
public class TencentImProperties {
    private Long sdkAppId;
    private String secretKey;
    private String adminUserId;
    private Long expireSeconds;
}
