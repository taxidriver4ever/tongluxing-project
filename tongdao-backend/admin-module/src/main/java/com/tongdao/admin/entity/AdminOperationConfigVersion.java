package com.tongdao.admin.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 运营配置版本实体，对应 admin_operation_config_version 表。
 */
@Data
public class AdminOperationConfigVersion {
    /** 主键 ID。 */
    private Long id;
    /** 关联配置主表 ID。 */
    private Long configId;
    /** 配置域。 */
    private String configDomain;
    /** 配置键。 */
    private String configKey;
    /** 版本号。 */
    private Integer versionNo;
    /** 当前版本配置值。 */
    private String configValue;
    /** 生效时间。 */
    private LocalDateTime effectiveAt;
    /** 操作人 ID。 */
    private Long operatorId;
    /** 变更原因。 */
    private String changeReason;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
