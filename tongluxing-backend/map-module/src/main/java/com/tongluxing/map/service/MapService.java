package com.tongluxing.map.service;

import java.math.BigDecimal;
import java.util.List;

import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.map.dto.RoutePlanRequest;
import com.tongluxing.map.vo.NearbyMapResponse;
import com.tongluxing.map.vo.LocationHistoryResponse;
import com.tongluxing.map.vo.LocationSearchResponse;
import com.tongluxing.map.vo.RoutePlanResponse;

/**
 * 地图模块业务服务契约。
 *
 * <p>向 Controller 屏蔽高德 HTTP 协议、MySQL 路线缓存、地点历史和距离计算细节。</p>
 */
public interface MapService {

    /** 规划真实驾车路线；相同有序路线点优先复用持久化缓存。 */
    RoutePlanResponse planRoute(RoutePlanRequest request);

    /** 校验用户已选地点并幂等记录最近使用历史。 */
    LocationDto resolveLocation(LocationDto location);

    /** 通过高德 Web 服务搜索真实 POI，可选返回距当前坐标的直线距离。 */
    List<LocationSearchResponse> searchLocations(
            String keyword, Integer limit, BigDecimal latitude, BigDecimal longitude);

    /** 查询当前用户最近选择的地点。 */
    List<LocationHistoryResponse> getLocationHistory(
            Integer limit, BigDecimal latitude, BigDecimal longitude);

    /** 删除当前用户的一条地点搜索历史，返回实际删除条数。 */
    int deleteLocationHistory(Long historyId);

    /** 清空当前用户的全部地点搜索历史，返回实际删除条数。 */
    int clearLocationHistory();

    /** 按中心坐标和半径查询附近高德 POI 地图标记。 */
    NearbyMapResponse getNearby(String latitude, String longitude, Integer radiusMeters);
}
