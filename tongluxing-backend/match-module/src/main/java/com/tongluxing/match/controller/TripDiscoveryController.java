package com.tongluxing.match.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.tongluxing.common.result.Result;
import com.tongluxing.match.dto.TripApplicationRequest;
import com.tongluxing.match.dto.TripSearchRequest;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.MatchApplyResponse;
import com.tongluxing.match.vo.TripSearchDetailResponse;
import com.tongluxing.match.vo.TripSearchPageResponse;
import com.tongluxing.match.vo.TripDiscoverPageResponse;
import com.tongluxing.match.vo.TripPublicDetailResponse;
import com.tongluxing.match.vo.TripConsultationResponse;
import com.tongluxing.team.dto.ReviewTeamApplicationRequest;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.team.vo.TeamApplicationResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 面向 App/小程序的行程搜索与申请接口。
 *
 * <p>搜索对象始终是公开招募行程；群聊权限只会在队长通过申请后由内部事件授予。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trips")
public class TripDiscoveryController {

    private final MatchService matchService;
    private final TeamService teamService;

    @PostMapping("/search")
    public Result<TripSearchPageResponse> search(@Valid @RequestBody TripSearchRequest request) {
        return Result.success(matchService.searchTrips(request));
    }

    @GetMapping("/discover")
    public Result<TripDiscoverPageResponse> discover(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String searchType,
            @RequestParam(defaultValue = "RECOMMENDED") String sort,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Long referenceTripId,
            @RequestParam(required = false) String startCity,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String departureDateFrom,
            @RequestParam(required = false) String departureDateTo,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) Integer minimumRemainingSeats,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return Result.success(matchService.discoverTrips(keyword, searchType, sort, latitude, longitude, referenceTripId,
                startCity, destination, departureDateFrom, departureDateTo, vehicleType,
                minimumRemainingSeats, page, size));
    }

    @GetMapping("/users/{userId}/public")
    public Result<TripDiscoverPageResponse> publicTripsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(matchService.getPublicTripsByUser(userId, page, size));
    }

    @GetMapping("/{tripId}/public-detail")
    public Result<TripPublicDetailResponse> publicDetail(@PathVariable Long tripId) {
        return Result.success(matchService.getPublicTripDetail(tripId));
    }

    @PostMapping("/{tripId}/favorite")
    public Result<Boolean> favorite(@PathVariable Long tripId) {
        return Result.success(matchService.favoriteTrip(tripId));
    }

    @DeleteMapping("/{tripId}/favorite")
    public Result<Boolean> unfavorite(@PathVariable Long tripId) {
        return Result.success(matchService.unfavoriteTrip(tripId));
    }

    @PostMapping("/{tripId}/consultations")
    public Result<TripConsultationResponse> consult(@PathVariable Long tripId,
                                                    @RequestBody(required = false) ConsultationBody body) {
        return Result.success(matchService.consultTrip(tripId, body == null ? null : body.content()));
    }

    /*
     * 通用 GET /v1/trips/{tripId} 已由 trip-module 提供。
     * 此接口补充发现同行所需的公开队长和车队容量信息，且不暴露群聊内容。
     */
    @GetMapping("/{tripId}/discovery-detail")
    public Result<TripSearchDetailResponse> detail(@PathVariable Long tripId) {
        return Result.success(matchService.getSearchTripDetail(tripId));
    }

    @PostMapping("/{tripId}/applications")
    public Result<MatchApplyResponse> apply(@PathVariable Long tripId,
                                            @Valid @RequestBody TripApplicationRequest request) {
        return Result.success(matchService.applyToTrip(tripId, request));
    }

    @GetMapping("/applications/my")
    public Result<List<TeamApplicationResponse>> myApplications() {
        return Result.success(teamService.getMyApplications());
    }

    @GetMapping("/{tripId}/applications")
    public Result<List<TeamApplicationResponse>> tripApplications(@PathVariable Long tripId) {
        return Result.success(teamService.getTripApplications(tripId));
    }

    @PostMapping("/applications/{applicationId}/approve")
    public Result<TeamApplicationResponse> approve(@PathVariable Long applicationId,
                                                    @RequestBody(required = false) ReviewBody body) {
        return Result.success(teamService.review(applicationId,
                new ReviewTeamApplicationRequest("APPROVE", body == null ? null : body.reason())));
    }

    @PostMapping("/applications/{applicationId}/reject")
    public Result<TeamApplicationResponse> reject(@PathVariable Long applicationId,
                                                   @RequestBody(required = false) ReviewBody body) {
        return Result.success(teamService.review(applicationId,
                new ReviewTeamApplicationRequest("REJECT", body == null ? null : body.reason())));
    }

    public record ReviewBody(String reason) {
    }

    public record ConsultationBody(String content) {
    }
}
