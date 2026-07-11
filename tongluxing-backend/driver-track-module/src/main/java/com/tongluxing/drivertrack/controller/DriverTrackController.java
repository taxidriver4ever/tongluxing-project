package com.tongluxing.drivertrack.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.drivertrack.dto.DriverTrackBatchRequest;
import com.tongluxing.drivertrack.dto.DriverTrackPointRequest;
import com.tongluxing.drivertrack.service.DriverTrackService;
import com.tongluxing.drivertrack.vo.DriverDeviationResponse;
import com.tongluxing.drivertrack.vo.DriverDistanceResponse;
import com.tongluxing.drivertrack.vo.DriverTrackListResponse;
import com.tongluxing.drivertrack.vo.DriverTrackUploadResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 驾驶端轨迹接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/driver-tracks")
public class DriverTrackController {

    private final DriverTrackService driverTrackService;

    @PostMapping("/points")
    public Result<DriverTrackUploadResponse> uploadPoint(@Valid @RequestBody DriverTrackPointRequest request) {
        return Result.success(driverTrackService.uploadPoint(request));
    }

    @PostMapping("/points/batch")
    public Result<DriverTrackListResponse> uploadBatch(@Valid @RequestBody DriverTrackBatchRequest request) {
        return Result.success(driverTrackService.uploadBatch(request));
    }

    @GetMapping("/trips/{tripId}")
    public Result<DriverTrackListResponse> getTrack(@PathVariable Long tripId) {
        return Result.success(driverTrackService.getTrack(tripId));
    }

    @GetMapping("/trips/{tripId}/deviation")
    public Result<DriverDeviationResponse> getDeviation(@PathVariable Long tripId) {
        return Result.success(driverTrackService.getDeviation(tripId));
    }

    @GetMapping("/trips/{tripId}/distance")
    public Result<DriverDistanceResponse> getDistance(@PathVariable Long tripId) {
        return Result.success(driverTrackService.getDistance(tripId));
    }
}
