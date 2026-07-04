package com.tongluxing.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 腾讯云 IM 配置项。
 *
 * <p>绑定 application.yml 中的 {@code tencent.im.*} 配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.im")
public class TencentImProperties {

    /** 腾讯云 IM SDKAppID。 */
    private Long sdkAppId;
    /** UserSig 签名密钥。 */
    private String secretKey;
    /** 服务端管理员账号 ID，用于调用 REST API。 */
    private String adminUserId;
    /** UserSig 有效期，单位秒。 */
    private Long expireSeconds;
}
