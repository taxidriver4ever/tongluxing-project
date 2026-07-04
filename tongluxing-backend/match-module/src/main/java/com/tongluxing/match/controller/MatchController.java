package com.tongluxing.match.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.MatchRecommendationListResponse;
import com.tongluxing.match.vo.NearbyTeamListResponse;
import com.tongluxing.match.vo.NearbyTripListResponse;

import lombok.RequiredArgsConstructor;

/**
 * 匹配模块接口控制器，提供行程推荐和附近行程/车队查询能力。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/matches")
public class MatchController {

    private final MatchService matchService;

    /**
     * 根据指定行程获取推荐行程和推荐车队。
     *
     * @param tripId 当前行程 ID
     * @param limit 返回数量上限
     * @return 推荐结果列表
     */
    @GetMapping("/trips/{tripId}/recommendations")
    public Result<MatchRecommendationListResponse> getTripRecommendations(@PathVariable Long tripId,
                                                                           @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(matchService.getTripRecommendations(tripId, limit));
    }

    /**
     * 查询当前位置附近的公开行程。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param radiusMeters 查询半径，单位米
     * @param limit 返回数量上限
     * @return 附近行程列表
     */
    @GetMapping("/nearby-trips")
    public Result<NearbyTripListResponse> getNearbyTrips(@RequestParam String latitude,
                                                         @RequestParam String longitude,
                                                         @RequestParam(defaultValue = "5000") Integer radiusMeters,
                                                         @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(matchService.getNearbyTrips(latitude, longitude, radiusMeters, limit));
    }

    /**
     * 查询当前位置附近的公开活跃车队。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param radiusMeters 查询半径，单位米
     * @param limit 返回数量上限
     * @return 附近车队列表
     */
    @GetMapping("/nearby-teams")
    public Result<NearbyTeamListResponse> getNearbyTeams(@RequestParam String latitude,
                                                         @RequestParam String longitude,
                                                         @RequestParam(defaultValue = "5000") Integer radiusMeters,
                                                         @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(matchService.getNearbyTeams(latitude, longitude, radiusMeters, limit));
    }
}
