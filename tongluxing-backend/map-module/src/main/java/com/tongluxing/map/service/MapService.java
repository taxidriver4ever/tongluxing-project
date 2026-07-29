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
 * 地图业务服务。
 */
public interface MapService {

    /** 规划路线并返回距离、时长、路线折线和路线点。 */
    RoutePlanResponse planRoute(RoutePlanRequest request);

    /** 解析地点并记录搜索/选择日志。 */
    LocationDto resolveLocation(LocationDto location);

    /** 通过高德 Web 服务搜索真实地点。 */
    List<LocationSearchResponse> searchLocations(
            String keyword, Integer limit, BigDecimal latitude, BigDecimal longitude);

    /** 查询当前用户最近选择的地点。 */
    List<LocationHistoryResponse> getLocationHistory(
            Integer limit, BigDecimal latitude, BigDecimal longitude);

    /** 删除当前用户的一条地点搜索历史，返回实际删除条数。 */
    int deleteLocationHistory(Long historyId);

    /** 清空当前用户的全部地点搜索历史，返回实际删除条数。 */
    int clearLocationHistory();

    /** 查询附近地图点位。 */
    NearbyMapResponse getNearby(String latitude, String longitude, Integer radiusMeters);
}
