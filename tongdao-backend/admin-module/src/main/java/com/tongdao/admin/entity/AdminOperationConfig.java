package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 运营配置主表实体，对应 admin_operation_config 表。
 */
@Data
public class AdminOperationConfig {
    /** 主键 ID。 */
    private Long id;
    /** 配置域，例如 GROWTH、INVITE、COUPON。 */
    private String configDomain;
    /** 配置键。 */
    private String configKey;
    /** 当前版本号。 */
    private Integer currentVersion;
    /** 配置状态。 */
    private String configStatus;
    /** 当前版本生效时间。 */
    private LocalDateTime effectiveAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0 未删除，1 已删除。 */
    private Integer deleted;
}
