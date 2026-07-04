package com.tongluxing.map.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 路线规划实体，对应 map_route_plan 表。
 */
@Data
public class MapRoutePlan {
    /** 主键 ID。 */
    private Long id;
    /** 发起规划的用户 ID。 */
    private Long userId;
    /** 路线点 JSON 的哈希值，用于缓存命中。 */
    private String routeHash;
    /** 起点、途经点、终点 JSON。 */
    private String routePointsJson;
    /** 路线规划结果 JSON。 */
    private String routeResultJson;
    /** 地图服务商类型。 */
    private String providerType;
    /** 规划状态。 */
    private String planStatus;
    /** 规划失败错误信息。 */
    private String errorMessage;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记。 */
    private Integer deleted;
}
