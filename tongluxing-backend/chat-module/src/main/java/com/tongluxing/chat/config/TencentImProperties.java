package com.tongluxing.chat.config;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Data;

/**
 * 腾讯云 IM 配置项。
 *
 * <p>聊天模块已经取消 MOCK 通道，本地、测试和生产环境都会使用真实腾讯 IM。
 * 因此应用启动时必须提供完整的 {@code tencent.im.*} 配置，缺少任何关键参数都会
 * 直接启动失败，避免运行过程中悄悄退回本地消息逻辑。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.im")
public class TencentImProperties {

    /** 腾讯云 IM SDKAppID。 */
    private Long sdkAppId;
    /** UserSig 签名密钥，只允许保存在后端环境变量中。 */
    private String secretKey;
    /** 服务端管理员账号 ID，用于调用腾讯 IM REST API。 */
    private String adminUserId;
    /** UserSig 有效期，单位秒。 */
    private Long expireSeconds;
    /** 腾讯 IM 控制台配置的回调鉴权 Token。 */
    private String callbackToken;

    /**
     * 启动时强制校验真实腾讯 IM 配置。
     *
     * <p>这里不再识别 provider-type，也没有 MOCK 降级分支。配置不完整时立即失败，
     * 让部署问题在启动阶段暴露，而不是等到用户发送消息时才报错。</p>
     */
    @PostConstruct
    public void validate() {
        if (!hasCredentials()) {
            throw new IllegalStateException(
                    "腾讯云 IM 配置不完整，请检查 SDKAppID、SecretKey、管理员账号、回调 Token 和 UserSig 有效期");
        }
    }

    /** 判断腾讯 IM 登录、REST API 和回调鉴权所需参数是否全部可用。 */
    public boolean hasCredentials() {
        return sdkAppId != null && sdkAppId > 0
                && StringUtils.hasText(secretKey)
                && StringUtils.hasText(adminUserId)
                && StringUtils.hasText(callbackToken)
                && expireSeconds != null && expireSeconds > 0;
    }
}
