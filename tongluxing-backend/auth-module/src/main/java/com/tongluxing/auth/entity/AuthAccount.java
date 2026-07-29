package com.tongluxing.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 认证账号实体，对应 auth_account 表。
 *
 * <p>这里只保存登录认证需要的信息，用户昵称、头像、成长值等资料归 user-module 管理。</p>
 *
 * <p>{@code id} 是认证表记录主键，{@code userId} 是跨模块稳定业务身份，两者刻意分离。
 * 手机号负责登录定位；账号禁用、引导完成度和最近登录信息属于认证域状态。
 * 密码和设备分别存放在独立表中，避免账号主表承担可选凭据及一对多终端数据。</p>
 */
@Data
public class AuthAccount {

    /** 主键 ID。 */
    private Long id;

    /** 业务用户 ID，其他模块以该字段作为用户身份标识。 */
    private Long userId;

    /** 登录手机号。 */
    private String phone;

    /** 账号状态：1 正常，2 禁用。 */
    private Integer accountStatus;

    /** 小程序邀请码引导是否已完成：0 未完成，1 已进入过邀请码页。 */
    private Integer miniInviteOnboardingCompleted;

    /** 最近一次登录时间。 */
    private LocalDateTime lastLoginTime;

    /** 最近一次登录 IP。 */
    private String lastLoginIp;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 未删除，1 已删除。 */
    private Integer deleted;
}
