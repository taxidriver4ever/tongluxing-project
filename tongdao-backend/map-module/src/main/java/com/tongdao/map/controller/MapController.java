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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/map")
public class MapController {

    private final MapService mapService;

    @PostMapping("/routes/plan")
    public Result<RoutePlanResponse> planRoute(@Valid @RequestBody RoutePlanRequest request) {
        return Result.success(mapService.planRoute(request));
    }

    @PostMapping("/locations/resolve")
    public Result<LocationDto> resolveLocation(@Valid @RequestBody LocationDto location) {
        return Result.success(mapService.resolveLocation(location));
    }

    @GetMapping("/nearby")
    public Result<NearbyMapResponse> getNearby(@RequestParam String latitude,
                                               @RequestParam String longitude,
                                               @RequestParam(defaultValue = "5000") Integer radiusMeters) {
        return Result.success(mapService.getNearby(latitude, longitude, radiusMeters));
    }
}
