package com.tongdao.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMiniProgramProperties {

    private String appId;

    private String appSecret;
}
