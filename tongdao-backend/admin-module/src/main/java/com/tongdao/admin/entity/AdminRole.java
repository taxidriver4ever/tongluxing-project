package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 后台角色实体，对应 admin_role 表。
 */
@Data
public class AdminRole {
    /** 主键 ID。 */
    private Long id;
    /** 角色编码。 */
    private String roleCode;
    /** 角色名称。 */
    private String roleName;
    /** 权限 JSON。 */
    private String permissionJson;
    /** 角色状态。 */
    private String roleStatus;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
