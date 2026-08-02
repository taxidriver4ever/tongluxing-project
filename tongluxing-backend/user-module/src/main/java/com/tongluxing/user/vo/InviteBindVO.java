package com.tongluxing.user.vo;

import java.time.LocalDateTime;

/**
 * 邀请绑定结果返回对象。
 *
 * @param inviterUserId 邀请人用户 ID
 * @param inviteeUserId 被邀请人用户 ID
 * @param status 绑定关系状态
 * @param boundAt 成功绑定时间
 */
public record InviteBindVO(Long inviterUserId, Long inviteeUserId, String status, LocalDateTime boundAt) {
}

