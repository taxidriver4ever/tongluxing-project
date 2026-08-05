package com.tongluxing.trip.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.Result;
import com.tongluxing.trip.dto.CreateTripRequest;
import com.tongluxing.trip.dto.ContinueTripRequest;
import com.tongluxing.trip.dto.StartTripRequest;
import com.tongluxing.trip.dto.UpdateTripRequest;
import com.tongluxing.trip.dto.TripTimeConflictRequest;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.service.TripSettlementService;
import com.tongluxing.trip.vo.ActiveTripStateResponse;
import com.tongluxing.trip.vo.ArrivalDecisionResponse;
import com.tongluxing.trip.vo.MyTripDashboardResponse;
import com.tongluxing.trip.vo.TripListResponse;
import com.tongluxing.trip.vo.TripMemberSnapshotResponse;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.trip.vo.TripSettlementResponse;
import com.tongluxing.trip.vo.TripTimeConflictResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 行程模块接口控制器，提供行程发布、查询、编辑、结束、取消和成员快照查询能力。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trips")
public class TripController {

    private final TripService tripService;
    private final TripSettlementService tripSettlementService;

    /**
     * 创建并发布一条行程。
     */
    @PostMapping
    public Result<TripResponse> createTrip(@Valid @RequestBody CreateTripRequest request) {
        return Result.success(tripService.createTrip(request));
    }

    /**
     * 查询当前用户的活跃或历史行程。
     */
    @GetMapping("/me")
    public Result<TripListResponse> getMyTrips(@RequestParam(defaultValue = "active") String scope) {
        return Result.success(tripService.getMyTrips(scope));
    }

    /** 查询用户当前拥有或参加的进行中行程。 */
    @GetMapping("/me/active-state")
    public Result<ActiveTripStateResponse> getActiveTripState() {
        return Result.success(tripService.getActiveTripState());
    }

    /** App「我的行程」首页聚合数据。 */
    @GetMapping("/me/dashboard")
    public Result<MyTripDashboardResponse> getMyTripDashboard() {
        return Result.success(tripService.getMyTripDashboard());
    }

    /** 发布前检查预计时间冲突；冲突仅提醒，用户仍可确认继续发布。 */
    @PostMapping("/time-conflicts/check")
    public Result<TripTimeConflictResponse> checkTimeConflict(
            @Valid @RequestBody TripTimeConflictRequest request) {
        return Result.success(tripService.checkTimeConflict(request));
    }

    /**
     * 查询公开行程列表，用于发现和匹配候选池。
     */
    @GetMapping("/public")
    public Result<TripListResponse> getPublicTrips(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(tripService.getPublicTrips(limit));
    }

    /**
     * 查询当前用户正在驾驶中的行程。
     */
    @GetMapping("/driving/current")
    public Result<TripResponse> getCurrentDrivingTrip() {
        return Result.success(tripService.getCurrentDrivingTrip());
    }

    /**
     * 查询行程详情。
     */
    @GetMapping("/{tripId}")
    public Result<TripResponse> getTrip(@PathVariable Long tripId) {
        return Result.success(tripService.getTrip(tripId));
    }

    /**
     * 编辑当前用户拥有的行程。
     */
    @PutMapping("/{tripId}")
    public Result<TripResponse> updateTrip(@PathVariable Long tripId, @Valid @RequestBody UpdateTripRequest request) {
        return Result.success(tripService.updateTrip(tripId, request));
    }

    /**
     * 开始当前用户拥有的行程。
     */
    @PostMapping("/{tripId}/start")
    public Result<TripResponse> startTrip(
            @PathVariable Long tripId,
            @Valid @RequestBody StartTripRequest request,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType) {
        rejectMiniProgramStart(clientType);
        return Result.success(tripService.startTrip(
                tripId, request.latitude(), request.longitude(), request.accuracy()));
    }

    private void rejectMiniProgramStart(String clientType) {
        if ("MINI_PROGRAM".equalsIgnoreCase(clientType)) {
            throw new BusinessException("小程序暂不支持开启行程，请下载同路行 App 使用此功能");
        }
    }

    /**
     * 结束当前用户拥有的行程。
     */
    @PostMapping("/{tripId}/end")
    public Result<TripResponse> endTrip(@PathVariable Long tripId) {
        return Result.success(tripService.endTrip(tripId));
    }


    /** 查询到达终点后的开放式结束状态。 */
    @GetMapping("/{tripId}/arrival")
    public Result<ArrivalDecisionResponse> getArrivalDecision(@PathVariable Long tripId) {
        return Result.success(tripService.getArrivalDecision(tripId));
    }

    /** 队长确认结束已经到达终点的行程。 */
    @PostMapping("/{tripId}/arrival/end")
    public Result<TripResponse> finishArrival(@PathVariable Long tripId) {
        return Result.success(tripService.finishArrival(tripId));
    }

    /** 队长选择继续行程，并提交新的终点。 */
    @PostMapping("/{tripId}/arrival/continue")
    public Result<TripResponse> continueTrip(@PathVariable Long tripId,
                                             @Valid @RequestBody ContinueTripRequest request) {
        return Result.success(tripService.continueTrip(tripId, request));
    }

    /** 对已结束行程执行幂等成长值结算，并推进到 SETTLED。 */
    @PostMapping("/{tripId}/settle")
    public Result<TripSettlementResponse> settleTrip(@PathVariable Long tripId) {
        return Result.success(tripSettlementService.settle(tripId));
    }

    /**
     * 取消当前用户拥有的行程。
     */
    @PostMapping("/{tripId}/cancel")
    public Result<TripResponse> cancelTrip(@PathVariable Long tripId) {
        return Result.success(tripService.cancelTrip(tripId));
    }

    /**
     * 查询行程成员快照。
     */
    @GetMapping("/{tripId}/members")
    public Result<List<TripMemberSnapshotResponse>> getMembers(@PathVariable Long tripId) {
        return Result.success(tripService.getMembers(tripId));
    }
}
