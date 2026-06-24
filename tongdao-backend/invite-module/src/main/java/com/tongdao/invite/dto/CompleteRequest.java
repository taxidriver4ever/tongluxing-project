package com.tongdao.invite.dto;

/**
 * 首次组队完成事件请求。
 *
 * @param userId 完成首次组队的用户 ID，也就是被邀请人
 * @param teamId 完成的队伍 ID
 * @param bizId 业务幂等号，通常由上游事件或订单/队伍完成记录生成
 */
public record CompleteRequest(
        Long userId,
        Long teamId,
        String bizId
) {
}
