package com.tongdao.map.service;

import com.tongdao.map.dto.LocationDto;
import com.tongdao.map.dto.RoutePlanRequest;
import com.tongdao.map.vo.NearbyMapResponse;
import com.tongdao.map.vo.RoutePlanResponse;

/**
 * 地图业务服务。
 */
public interface MapService {

    /** 规划路线并返回距离、时长、路线折线和路线点。 */
    RoutePlanResponse planRoute(RoutePlanRequest request);

    /** 解析地点并记录搜索/选择日志。 */
    LocationDto resolveLocation(LocationDto location);

    /** 查询附近地图点位。 */
    NearbyMapResponse getNearby(String latitude, String longitude, Integer radiusMeters);
}
