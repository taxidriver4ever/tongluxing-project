package com.tongluxing.trip.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.trip.dto.TripDraftSaveRequest;
import com.tongluxing.trip.dto.TripWaypointCommand;
import com.tongluxing.trip.dto.TripWaypointOrderRequest;
import com.tongluxing.trip.service.TripCreationService;
import com.tongluxing.trip.vo.TripCreationWaypointResponse;
import com.tongluxing.trip.vo.TripDraftDetailResponse;
import com.tongluxing.trip.vo.TripDraftPublishResponse;
import com.tongluxing.trip.vo.TripDraftRouteResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 微信端创建行程专用接口；与历史复数路径并存。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trip")
public class TripCreationController {
    private final TripCreationService service;

    @PostMapping("/draft")
    public Result<TripDraftDetailResponse> createDraft(
            @Valid @RequestBody(required = false) TripDraftSaveRequest request) {
        return Result.success(service.createDraft(request));
    }

    @GetMapping("/draft/{draftId}")
    public Result<TripDraftDetailResponse> getDraft(@PathVariable Long draftId) {
        return Result.success(service.getDraft(draftId));
    }

    @GetMapping("/draft")
    public Result<List<TripDraftDetailResponse>> listDrafts(@RequestParam(defaultValue = "DRAFT") String status) {
        return Result.success(service.listDrafts(status));
    }

    @PutMapping("/draft/{draftId}")
    public Result<TripDraftDetailResponse> updateDraft(@PathVariable Long draftId,
            @Valid @RequestBody TripDraftSaveRequest request) {
        return Result.success(service.updateDraft(draftId, request));
    }

    @DeleteMapping("/draft/{draftId}")
    public Result<Void> deleteDraft(@PathVariable Long draftId) {
        service.deleteDraft(draftId);
        return Result.success();
    }

    @PostMapping("/{draftId}/waypoint")
    public Result<TripCreationWaypointResponse> addWaypoint(@PathVariable Long draftId,
            @Valid @RequestBody TripWaypointCommand request) {
        return Result.success(service.addWaypoint(draftId, request));
    }

    @PutMapping("/{draftId}/waypoint/{waypointId}")
    public Result<TripCreationWaypointResponse> updateWaypoint(@PathVariable Long draftId,
            @PathVariable Long waypointId, @Valid @RequestBody TripWaypointCommand request) {
        return Result.success(service.updateWaypoint(draftId, waypointId, request));
    }

    @DeleteMapping("/{draftId}/waypoint/{waypointId}")
    public Result<Void> deleteWaypoint(@PathVariable Long draftId, @PathVariable Long waypointId) {
        service.deleteWaypoint(draftId, waypointId);
        return Result.success();
    }

    @PutMapping("/{draftId}/waypoints/order")
    public Result<List<TripCreationWaypointResponse>> reorder(@PathVariable Long draftId,
            @Valid @RequestBody TripWaypointOrderRequest request) {
        return Result.success(service.reorderWaypoints(draftId, request));
    }

    @PostMapping("/draft/{draftId}/route/plan")
    public Result<TripDraftRouteResponse> planRoute(@PathVariable Long draftId) {
        return Result.success(service.planRoute(draftId));
    }

    @GetMapping("/{draftId}/route")
    public Result<TripDraftRouteResponse> getRoute(@PathVariable Long draftId) {
        return Result.success(service.getRoute(draftId));
    }

    @PostMapping("/draft/{draftId}/publish")
    public Result<TripDraftPublishResponse> publish(@PathVariable Long draftId) {
        return Result.success(service.publish(draftId));
    }
}
