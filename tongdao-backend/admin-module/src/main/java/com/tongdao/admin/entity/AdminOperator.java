package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 后台操作员实体，对应 admin_operator 表。
 */
@Data
public class AdminOperator {
    /** 主键 ID。 */
    private Long id;
    /** 登录用户名。 */
    private String username;
    /** 展示名称。 */
    private String displayName;
    /** 手机号。 */
    private String phone;
    /** 密码哈希。 */
    private String passwordHash;
    /** 操作员状态。 */
    private String operatorStatus;
    /** 最近登录时间。 */
    private LocalDateTime lastLoginAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
