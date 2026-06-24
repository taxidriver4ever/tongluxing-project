package com.tongdao.growth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 成长徽章实体。
 *
 * <p>对应 growth_badge 表，用于描述某类业务事件达到指定次数后可以授予的徽章规则。</p>
 */
@Data
public class GrowthBadge {
    /** 徽章主键 ID。 */
    private Long id;

    /** 徽章唯一编码。 */
    private String badgeCode;

    /** 徽章展示名称。 */
    private String badgeName;

    /** 徽章图片资源标识。 */
    private String badgeImageKey;

    /** 触发徽章的业务事件类型。 */
    private String eventType;

    /** 达到该事件次数后授予徽章。 */
    private Integer threshold;

    /** 是否启用该徽章规则。 */
    private Boolean enabledFlag;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，0 表示正常。 */
    private Integer deleted;
}
