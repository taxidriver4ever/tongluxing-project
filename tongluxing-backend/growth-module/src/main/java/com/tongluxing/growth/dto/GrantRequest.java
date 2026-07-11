package com.tongluxing.growth.dto;

/**
 * 成长值发放请求。
 *
 * @param userId 目标用户 ID
 * @param bizType 业务类型，例如邀请、下单、评价等
 * @param bizId 业务唯一 ID，用于保证同一业务只发放一次
 * @param points 成长值变化量，正数表示发放，负数表示扣减
 * @param remark 流水备注，便于前端展示和后台排查
 */
public record GrantRequest(Long userId, String bizType, String bizId, Integer points, String remark) {
}