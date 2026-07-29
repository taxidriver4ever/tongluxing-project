package com.tongluxing.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 当前联调阶段的 Web Admin 固定账号与安全策略配置。
 *
 * <p>属性绑定 {@code admin.auth.*}。Admin 登录与普通用户账号体系隔离：
 * 凭据来自部署配置，登录成功后签发 Redis 不透明会话，不创建 {@code auth_account}，
 * 也不签发用户 JWT。密码必须由部署环境注入。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin.auth")
public class AdminAuthProperties {
    /** 固定后台登录名；比较与限流前会转换为小写。 */
    private String username = "admin";
    /** 后台密码必须通过部署环境外置并妥善保管。 */
    private String password;
    /** 后台操作员 ID，写入 Admin 会话并供业务审计使用。 */
    private Long operatorId = 10001L;
    /** 后台界面显示的操作员名称。 */
    private String displayName = "运营管理员";
    /** Admin 不透明会话有效期，单位秒。 */
    private Integer sessionExpireSeconds = 7200;
    /** 固定窗口内允许的最大连续失败次数，达到后进入锁定状态。 */
    private Integer maxFailures = 5;
    /** 登录失败计数及锁定窗口，单位秒。 */
    private Integer failureWindowSeconds = 3600;
}
