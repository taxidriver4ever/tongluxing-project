package com.tongluxing.invite.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 邀请模块查询结果 DTO。
 *
 * <p>用于承接 Mapper 查询结果，覆盖邀请码、邀请关系和奖励进度等多个查询场景。</p>
 */
@Data
public class InviteQueryDTO {

    /** 通用主键 ID。 */
    private Long id;

    /** 邀请码所属用户 ID。 */
    private Long userId;

    /** 邀请码字符串。 */
    private String inviteCode;

    /** 邀请码是否启用。 */
    private Boolean enabledFlag;

    /** 邀请关系 ID。 */
    private Long relationId;

    /** 邀请人用户 ID。 */
    private Long inviterUserId;

    /** 被邀请人用户 ID。 */
    private Long inviteeUserId;

    /** 邀请关系状态：BOUND 已绑定，VALID 已完成有效行为。 */
    private String status;

    /** 绑定来源：INVITE。 */
    private String bindSource;

    /** 绑定来源值：系统分享参数或邀请人手机号脱敏值。 */
    private String bindSourceValue;

    /** 被邀请人注册时间快照。 */
    private LocalDateTime inviteeRegisteredAt;

    /** 邀请关系绑定时间。 */
    private LocalDateTime boundAt;

    /** 被邀请人首次完成组队的时间。 */
    private LocalDateTime firstTeamCompletedAt;
}
