package com.tongluxing.map.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.map.dto.RoutePlanRequest;
import com.tongluxing.map.service.MapService;
import com.tongluxing.map.vo.NearbyMapResponse;
import com.tongluxing.map.vo.LocationHistoryResponse;
import com.tongluxing.map.vo.LocationSearchResponse;
import com.tongluxing.map.vo.RoutePlanResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 地图模块接口。
 *
 * <p>提供路线规划、地点解析和附近地图点位查询能力。驾车路线由高德 Web 服务
 * 返回真实道路折线，并在多个候选方案中选择距离最短的路线。</p>
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
        // @Valid 校验起终点和途经点数量，Service 再校验每个坐标值范围。
        return Result.success(mapService.planRoute(request));
    }

    /** 解析并记录用户选择的地点。 */
    @PostMapping("/locations/resolve")
    public Result<LocationDto> resolveLocation(@Valid @RequestBody LocationDto location) {
        // 只在用户确认选中地点时调用，搜索候选本身不会落入历史。
        return Result.success(mapService.resolveLocation(location));
    }

    /** 通过高德 POI 搜索 2.0 批量搜索真实地点。 */
    @GetMapping("/locations/search")
    public Result<List<LocationSearchResponse>> searchLocations(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude) {
        // 当前经纬度可选，成对提供时响应会附加球面直线距离。
        return Result.success(mapService.searchLocations(keyword, limit, latitude, longitude));
    }

    /** 查询当前登录用户的最近地点选择历史。 */
    @GetMapping("/locations/history")
    public Result<List<LocationHistoryResponse>> locationHistory(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude) {
        // 历史仅返回当前登录用户曾经 resolve 成功的地点。
        return Result.success(mapService.getLocationHistory(limit, latitude, longitude));
    }

    /** 删除当前登录用户的一条地点搜索历史。 */
    @DeleteMapping("/locations/history/{historyId}")
    public Result<Integer> deleteLocationHistory(@PathVariable Long historyId) {
        // 返回实际逻辑删除行数，不属于当前用户时业务层返回 404。
        return Result.success(mapService.deleteLocationHistory(historyId));
    }

    /** 清空当前登录用户的全部地点搜索历史。 */
    @DeleteMapping("/locations/history")
    public Result<Integer> clearLocationHistory() {
        // 清空操作幂等，已无记录时正常返回 0。
        return Result.success(mapService.clearLocationHistory());
    }

    /** 查询指定经纬度附近的地图标记点。 */
    @GetMapping("/nearby")
    public Result<NearbyMapResponse> getNearby(@RequestParam String latitude,
                                               @RequestParam String longitude,
                                               @RequestParam(defaultValue = "5000") Integer radiusMeters) {
        // 坐标以字符串接收便于给出统一格式错误，Service 转为 BigDecimal 后再校验。
        return Result.success(mapService.getNearby(latitude, longitude, radiusMeters));
    }
}
