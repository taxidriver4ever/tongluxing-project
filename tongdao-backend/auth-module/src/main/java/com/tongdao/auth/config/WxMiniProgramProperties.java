package com.tongdao.auth.config;

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

    /** 微信小程序 appId。 */
    private String appId;

    /** 微信小程序 appSecret，仅服务端使用，不能下发给前端。 */
    private String appSecret;
}
