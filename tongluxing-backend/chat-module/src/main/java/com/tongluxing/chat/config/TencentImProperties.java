package com.tongluxing.chat.config;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    /** 聊天通道：MOCK 或 TENCENT_IM。 */
    private String providerType = "MOCK";
    /** 腾讯云 IM SDKAppID。 */
    private Long sdkAppId;
    /** UserSig 签名密钥。 */
    private String secretKey;
    /** 服务端管理员账号 ID，用于调用 REST API。 */
    private String adminUserId;
    /** UserSig 有效期，单位秒。 */
    private Long expireSeconds;

    /** 启动时拒绝未知通道，且生产选择腾讯 IM 时必须提供完整凭据。 */
    @PostConstruct
    public void validate() {
        String normalized = normalizedProviderType();
        if (!"MOCK".equals(normalized) && !"TENCENT_IM".equals(normalized)) {
            throw new IllegalStateException("tencent.im.provider-type 仅支持 MOCK 或 TENCENT_IM");
        }
        if ("TENCENT_IM".equals(normalized) && !hasCredentials()) {
            throw new IllegalStateException("已选择 TENCENT_IM，但腾讯云 IM 配置不完整");
        }
    }

    public String normalizedProviderType() {
        return StringUtils.hasText(providerType) ? providerType.trim().toUpperCase() : "MOCK";
    }

    public boolean hasCredentials() {
        return sdkAppId != null && sdkAppId > 0
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(adminUserId)
                && expireSeconds != null && expireSeconds > 0;
    }
}
