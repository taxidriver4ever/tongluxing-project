package com.tongluxing.application.trip.controller;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.application.trip.dto.DepartureExceptionActionRequest;
import com.tongluxing.application.trip.dto.TeamAlertActionRequest;
import com.tongluxing.application.trip.service.TripStateApplicationService;
import com.tongluxing.application.trip.vo.MapHomeStateResponse;
import com.tongluxing.trip.vo.TripResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** App 地图三态与队长异常处理接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/p0")
public class TripStateController {

    private final TripStateApplicationService stateService;

    /** 一次返回普通地图、行程预览或行程中地图所需的完整状态。 */
    @GetMapping("/map-home/state")
    public Result<MapHomeStateResponse> getMapHomeState() {
        return Result.success(stateService.getMapHomeState());
    }

    /** 队长查看自动出发前超出约 100km 或未定位的成员。 */
    @GetMapping("/trips/{tripId}/departure-exceptions")
    public Result<List<Map<String, Object>>> getDepartureExceptions(@PathVariable Long tripId) {
        return Result.success(stateService.getDepartureExceptions(tripId));
    }

    /** 队长选择等待，或忽略异常直接出发。 */
    @PostMapping("/trips/{tripId}/departure-exceptions/action")
    public Result<TripResponse> handleDepartureExceptions(
            @PathVariable Long tripId,
            @Valid @RequestBody DepartureExceptionActionRequest request) {
        return Result.success(stateService.handleDepartureExceptions(tripId, request));
    }

    /** 队长查看一级脱队、严重脱队和长时间失联异常。 */
    @GetMapping("/trips/{tripId}/team-alerts")
    public Result<List<Map<String, Object>>> getTeamAlerts(@PathVariable Long tripId) {
        return Result.success(stateService.getTeamAlerts(tripId));
    }

    /** 队长忽略异常或正式移除成员。 */
    @PostMapping("/team-alerts/{alertId}/action")
    public Result<Map<String, Object>> handleTeamAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody TeamAlertActionRequest request) {
        return Result.success(stateService.handleTeamAlert(alertId, request));
    }
}
