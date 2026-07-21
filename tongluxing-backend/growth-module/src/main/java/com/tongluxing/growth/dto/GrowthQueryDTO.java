package com.tongluxing.growth.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 成长模块查询结果 DTO。
 *
 * <p>该对象主要承接 Mapper 查询结果，覆盖成长账户、成长流水、徽章查询等多个读模型。
 * 字段按不同查询场景复用，服务层会再转换成面向接口返回的 VO。</p>
 */
@Data
public class GrowthQueryDTO {
    /** 通用主键 ID，可能来自账户、流水或其他查询结果。 */
    private Long id;

    /** 用户累计成长值。 */
    private Integer totalPoints;

    /** 当前等级编码，例如 LV1、LV2。 */
    private String levelCode;

    /** 成长账户乐观锁版本号。 */
    private Integer version;

    /** 成长值流水关联的业务类型。 */
    private String bizType;

    /** 成长值流水关联的业务唯一 ID。 */
    private String bizId;

    /** 本次成长值变化量。 */
    private Integer pointDelta;

    /** 本次变更后的成长值余额。 */
    private Integer balanceAfter;

    /** 流水备注。 */
    private String remark;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 徽章 ID。 */
    private Long badgeId;

    /** 徽章编码。 */
    private String badgeCode;

    /** 徽章名称。 */
    private String badgeName;

    /** 徽章图片资源标识。 */
    private String badgeImageKey;

    /** 勋章达成条件的人类可读描述。 */
    private String conditionDescription;

    /** 触发徽章的事件类型。 */
    private String eventType;

    /** 触发徽章所需的事件次数阈值。 */
    private Integer threshold;

    /** 当前用户在该规则上的已完成指标。 */
    private Integer currentValue;

    /** 用户获得徽章的时间；未获得徽章时为空。 */
    private LocalDateTime awardedAt;
}
