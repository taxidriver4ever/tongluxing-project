package com.tongluxing.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 微信小程序配置项。
 *
 * <p>绑定 application.yml 中的 {@code wx.miniapp.*} 配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMiniProgramProperties {

    /** 微信小程序 appId，作为 client_credential 请求的公开应用标识。 */
    private String appId;

    /** 微信小程序 appSecret，仅服务端调用微信接口使用，不能记录日志或下发给前端。 */
    private String appSecret;
}
