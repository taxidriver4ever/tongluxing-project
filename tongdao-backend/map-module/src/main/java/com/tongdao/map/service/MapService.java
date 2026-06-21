package com.tongdao.map.service;

import com.tongdao.map.dto.LocationDto;
import com.tongdao.map.dto.RoutePlanRequest;
import com.tongdao.map.vo.NearbyMapResponse;
import com.tongdao.map.vo.RoutePlanResponse;

public interface MapService {

    RoutePlanResponse planRoute(RoutePlanRequest request);

    LocationDto resolveLocation(LocationDto location);

    NearbyMapResponse getNearby(String latitude, String longitude, Integer radiusMeters);
}
