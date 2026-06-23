package com.tongdao.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 认证账号实体，对应 auth_account 表。
 *
 * <p>这里只保存登录认证需要的信息，用户昵称、头像、成长值等资料归 user-module 管理。</p>
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
