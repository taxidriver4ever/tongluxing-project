package com.tongluxing.chat.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 命中安全规则的消息风险记录；与普通聊天查询隔离。 */
@Data
public class MessageRisk {
    /** 风险记录主键。 */
    private Long id;
    /** 被规则命中的聊天消息 ID，一条消息至多对应一条风险记录。 */
    private Long messageId;
    /** 风险等级，例如 HIGH、MEDIUM。 */
    private String riskLevel;
    /** 风险分类，例如 FRAUD、ILLEGAL、AD、ABUSE。 */
    private String riskType;
    /** 本地规则给出的置信度，取值范围为 0 至 100。 */
    private Integer confidence;
    /** 审核状态，新记录默认为 PENDING。 */
    private String status;
    /** 命中的规则标识，供后台审核追溯判断依据。 */
    private String matchedRule;
    /** 风险记录创建时间。 */
    private LocalDateTime createdAt;
    /** 风险记录最后更新时间。 */
    private LocalDateTime updatedAt;
}
