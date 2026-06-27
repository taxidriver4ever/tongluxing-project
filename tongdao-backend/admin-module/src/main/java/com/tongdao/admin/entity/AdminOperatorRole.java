package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 后台操作员角色关联实体，对应 admin_operator_role 表。
 */
@Data
public class AdminOperatorRole {
    /** 主键 ID。 */
    private Long id;
    /** 操作员 ID。 */
    private Long operatorId;
    /** 角色 ID。 */
    private Long roleId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
