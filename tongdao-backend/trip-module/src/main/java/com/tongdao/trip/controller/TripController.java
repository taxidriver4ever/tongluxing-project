package com.tongdao.trip.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.trip.dto.CreateTripRequest;
import com.tongdao.trip.dto.UpdateTripRequest;
import com.tongdao.trip.service.TripService;
import com.tongdao.trip.vo.TripListResponse;
import com.tongdao.trip.vo.TripMemberSnapshotResponse;
import com.tongdao.trip.vo.TripResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trips")
public class TripController {

    private final TripService tripService;

    @PostMapping
    public Result<TripResponse> createTrip(@Valid @RequestBody CreateTripRequest request) {
        return Result.success(tripService.createTrip(request));
    }

    @GetMapping("/me")
    public Result<TripListResponse> getMyTrips(@RequestParam(defaultValue = "active") String scope) {
        return Result.success(tripService.getMyTrips(scope));
    }

    @GetMapping("/public")
    public Result<TripListResponse> getPublicTrips(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(tripService.getPublicTrips(limit));
    }

    @GetMapping("/{tripId}")
    public Result<TripResponse> getTrip(@PathVariable Long tripId) {
        return Result.success(tripService.getTrip(tripId));
    }

    @PutMapping("/{tripId}")
    public Result<TripResponse> updateTrip(@PathVariable Long tripId, @Valid @RequestBody UpdateTripRequest request) {
        return Result.success(tripService.updateTrip(tripId, request));
    }

    @PostMapping("/{tripId}/end")
    public Result<TripResponse> endTrip(@PathVariable Long tripId) {
        return Result.success(tripService.endTrip(tripId));
    }

    @PostMapping("/{tripId}/cancel")
    public Result<TripResponse> cancelTrip(@PathVariable Long tripId) {
        return Result.success(tripService.cancelTrip(tripId));
    }

    @GetMapping("/{tripId}/members")
    public Result<List<TripMemberSnapshotResponse>> getMembers(@PathVariable Long tripId) {
        return Result.success(tripService.getMembers(tripId));
    }
}
