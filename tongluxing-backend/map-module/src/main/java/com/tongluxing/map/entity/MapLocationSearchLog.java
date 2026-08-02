package com.tongluxing.map.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 地点搜索/选择日志实体，对应 map_location_search_log 表。
 */
@Data
public class MapLocationSearchLog {
    /** 主键 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 搜索关键词或展示名称。 */
    private String keyword;
    /** 用户选择的地点名称。 */
    private String selectedName;
    /** 用户选择的地点地址。 */
    private String selectedAddress;
    /** 用户选择地点纬度。 */
    private BigDecimal selectedLatitude;
    /** 用户选择地点经度。 */
    private BigDecimal selectedLongitude;
    /** 使用场景，例如 RESOLVE。 */
    private String scene;
    /** 地图服务商及接口版本，例如 AMAP_WEB_V5_POI。 */
    private String providerType;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0 有效，1 已删除。 */
    private Integer deleted;
}
