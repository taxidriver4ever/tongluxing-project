package com.tongdao.match.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.match.service.MatchService;
import com.tongdao.match.vo.MatchRecommendationListResponse;
import com.tongdao.match.vo.NearbyTeamListResponse;
import com.tongdao.match.vo.NearbyTripListResponse;

import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/matches")
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/trips/{tripId}/recommendations")
    public Result<MatchRecommendationListResponse> getTripRecommendations(@PathVariable Long tripId,
                                                                           @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(matchService.getTripRecommendations(tripId, limit));
    }

    @GetMapping("/nearby-trips")
    public Result<NearbyTripListResponse> getNearbyTrips(@RequestParam String latitude,
                                                         @RequestParam String longitude,
                                                         @RequestParam(defaultValue = "5000") Integer radiusMeters,
                                                         @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(matchService.getNearbyTrips(latitude, longitude, radiusMeters, limit));
    }

    @GetMapping("/nearby-teams")
    public Result<NearbyTeamListResponse> getNearbyTeams(@RequestParam String latitude,
                                                         @RequestParam String longitude,
                                                         @RequestParam(defaultValue = "5000") Integer radiusMeters,
                                                         @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(matchService.getNearbyTeams(latitude, longitude, radiusMeters, limit));
    }
}
