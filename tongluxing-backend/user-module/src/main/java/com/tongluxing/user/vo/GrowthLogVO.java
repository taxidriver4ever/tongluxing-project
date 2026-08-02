package com.tongluxing.user.vo;

import java.time.LocalDateTime;

/**
 * 成长值流水返回对象。
 *
 * <p>pointDelta 正数表示增加、负数表示扣减；balanceAfter 是该笔变动后的余额，
 * 便于客户端展示和后台审计。</p>
 *
 * @param id 流水主键
 * @param bizType 产生变动的业务类型
 * @param bizId 关联业务单据 ID
 * @param pointDelta 本次成长值增减量
 * @param balanceAfter 变动后的成长值余额
 * @param remark 展示或审计备注
 * @param createdAt 流水创建时间
 */
public record GrowthLogVO(
        Long id, String bizType, String bizId, Integer pointDelta, Integer balanceAfter,
        String remark, LocalDateTime createdAt
) {
}

