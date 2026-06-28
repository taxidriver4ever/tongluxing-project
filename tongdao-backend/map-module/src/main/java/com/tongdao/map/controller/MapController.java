package com.tongdao.map.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.map.dto.LocationDto;
import com.tongdao.map.dto.RoutePlanRequest;
import com.tongdao.map.service.MapService;
import com.tongdao.map.vo.NearbyMapResponse;
import com.tongdao.map.vo.RoutePlanResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 地图模块接口。
 *
 * <p>提供路线规划、地点解析和附近地图点位查询能力。当前底层实现为本地 MOCK 计算，
 * 后续可替换为真实地图服务商。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/map")
public class MapController {

    /** 地图业务服务。 */
    private final MapService mapService;

    /** 根据起点、终点和途经点生成路线规划结果。 */
    @PostMapping("/routes/plan")
    public Result<RoutePlanResponse> planRoute(@Valid @RequestBody RoutePlanRequest request) {
        return Result.success(mapService.planRoute(request));
    }

    /** 解析并记录用户选择的地点。 */
    @PostMapping("/locations/resolve")
    public Result<LocationDto> resolveLocation(@Valid @RequestBody LocationDto location) {
        return Result.success(mapService.resolveLocation(location));
    }

    /** 查询指定经纬度附近的地图标记点。 */
    @GetMapping("/nearby")
    public Result<NearbyMapResponse> getNearby(@RequestParam String latitude,
                                               @RequestParam String longitude,
                                               @RequestParam(defaultValue = "5000") Integer radiusMeters) {
        return Result.success(mapService.getNearby(latitude, longitude, radiusMeters));
    }
}
