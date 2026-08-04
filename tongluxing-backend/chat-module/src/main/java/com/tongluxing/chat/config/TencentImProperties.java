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
    /** 腾讯 IM 控制台配置的回调鉴权 Token；为空时拒绝生产回调。 */
    private String callbackToken;

    /** 启动时拒绝未知通道，且生产选择腾讯 IM 时必须提供完整凭据。 */
    @PostConstruct
    public void validate() {
        // 先规范化大小写和首尾空格，避免配置书写差异造成错误分支。
        String normalized = normalizedProviderType();
        if (!"MOCK".equals(normalized) && !"TENCENT_IM".equals(normalized)) {
            throw new IllegalStateException("tencent.im.provider-type 仅支持 MOCK 或 TENCENT_IM");
        }
        if ("TENCENT_IM".equals(normalized) && !hasCredentials()) {
            // 真实通道配置不完整时启动即失败，避免运行到发送消息时才暴露配置问题。
            throw new IllegalStateException("已选择 TENCENT_IM，但腾讯云 IM 配置不完整");
        }
    }

    /** 返回标准大写的通道类型；未配置或空白时安全回退到 MOCK。 */
    public String normalizedProviderType() {
        return StringUtils.hasText(providerType) ? providerType.trim().toUpperCase() : "MOCK";
    }

    /**
     * 判断 SDKAppID、签名密钥、管理员、回调 Token 和有效期是否均为可用值。
     *
     * <p>当前架构依赖发送前/发送后回调完成权限、风控和审计，因此真实通道不能
     * 在未配置 callbackToken 的情况下启动。</p>
     */
    public boolean hasCredentials() {
        return sdkAppId != null && sdkAppId > 0
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(adminUserId)
                && StringUtils.hasText(callbackToken)
                && expireSeconds != null && expireSeconds > 0;
    }
}
