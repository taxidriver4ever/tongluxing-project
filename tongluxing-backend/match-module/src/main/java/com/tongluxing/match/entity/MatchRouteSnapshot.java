package com.tongluxing.match.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 行程发布时供匹配算法使用的路线快照。
 *
 * <p>快照把匹配所需字段从持续变化的行程聚合中固化出来，使推荐计算可以复现当时的
 * 起终点、路线和公开状态。相同 tripId 通过数据库唯一键更新已有快照。</p>
 */
@Data
public class MatchRouteSnapshot {
    /** 快照雪花主键。 */
    private Long id;
    /** 对应行程 ID，一条有效行程只保留一个有效快照。 */
    private Long tripId;
    /** 行程创建者用户 ID。 */
    private Long userId;
    /** 行程主车辆 ID。 */
    private Long vehicleId;
    /** 起点名称。 */
    private String startName;
    /** 起点完整地址。 */
    private String startAddress;
    /** 起点纬度。 */
    private BigDecimal startLatitude;
    /** 起点经度。 */
    private BigDecimal startLongitude;
    /** 终点名称。 */
    private String endName;
    /** 终点完整地址。 */
    private String endAddress;
    /** 终点纬度。 */
    private BigDecimal endLatitude;
    /** 终点经度。 */
    private BigDecimal endLongitude;
    /** 路线折线或途经点 JSON。 */
    private String routePointsJson;
    /** 规划路线距离，单位米。 */
    private Integer routeDistance;
    /** 规划路线耗时，单位秒。 */
    private Integer routeDuration;
    /** 计划出发时间。 */
    private LocalDateTime departureTime;
    /** 旅行深度/节奏分类，用于兴趣相似度评分。 */
    private String travelDepth;
    /** 最大可接纳车辆数。 */
    private Integer maxVehicleCount;
    /** 是否允许公开匹配：1 公开，0 私有。 */
    private Integer publicFlag;
    /** 快照状态，例如 ACTIVE。 */
    private String snapshotStatus;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最近同步时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标志。 */
    private Integer deleted;
}
