package com.tongluxing.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户密码凭证实体，对应 {@code auth_password_credential} 表。
 *
 * <p>认证账号与密码凭证分表存储：验证码或微信登录用户可以在没有密码凭证的情况下存在，
 * 首次设置密码后才创建本记录。任何场景都只保存不可逆哈希，不保存或记录明文密码。</p>
 */
@Data
public class AuthPasswordCredential {
    /** 密码凭证主键，使用雪花算法生成。 */
    private Long id;
    /** 凭证所属的全局业务用户 ID；每个用户最多一条未删除凭证。 */
    private Long userId;
    /** PasswordEncoder 生成的完整 BCrypt 哈希，包含算法参数与随机盐。 */
    private String passwordHash;
    /** 密码算法版本，当前为 BCRYPT，便于未来迁移算法时识别旧数据。 */
    private String passwordVersion;
    /** 凭证状态：1 可用；其他状态可用于禁用密码登录而不删除账号。 */
    private Integer passwordStatus;
    /** 最近一次设置密码的时间。 */
    private LocalDateTime lastSetTime;
    /** 凭证创建时间。 */
    private LocalDateTime createdAt;
    /** 凭证更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0 未删除，1 已删除。 */
    private Integer deleted;
}
