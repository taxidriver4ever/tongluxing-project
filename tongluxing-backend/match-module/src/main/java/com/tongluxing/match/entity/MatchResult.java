package com.tongluxing.match.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 两条行程之间一次有效匹配计算的持久化结果。
 *
 * <p>source 表示用户自己的基准行程，target 表示被推荐行程；两者有方向，不能交换
 * 解释。相同 source/target 组合由数据库唯一键执行 upsert，规则重算不会不断新增
 * 重复记录。</p>
 */
@Data
public class MatchResult {
    /** 匹配结果雪花主键，同时作为推荐详情 matchId。 */
    private Long id;
    /** 发起匹配计算的基准行程 ID。 */
    private Long sourceTripId;
    /** 推荐给源行程的候选行程 ID。 */
    private Long targetTripId;
    /** 源行程创建者用户 ID，用于推荐访问权限校验。 */
    private Long sourceUserId;
    /** 目标行程创建者用户 ID。 */
    private Long targetUserId;
    /** 综合匹配分，范围 0~100。 */
    private Integer matchScore;
    /** 路线重合度近似值，范围 0~100。 */
    private Integer overlapRate;
    /** 起点与终点平均空间偏差的近似值，单位米。 */
    private Integer distanceGapMeters;
    /** 两条行程出发时间绝对差，单位分钟。 */
    private Integer departureGapMinutes;
    /** 各评分维度明细 JSON，用于解释推荐和规则调优。 */
    private String scoreDetailJson;
    /** 结果状态；VALID 才能用于线上推荐。 */
    private String resultStatus;
    /** 本次规则计算完成时间。 */
    private LocalDateTime calculatedAt;
    /** 首次创建时间。 */
    private LocalDateTime createdAt;
    /** 最近一次重算更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标志：0 有效，1 删除。 */
    private Integer deleted;
}
