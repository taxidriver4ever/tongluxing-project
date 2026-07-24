package com.tongluxing.trip.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.TripDiscoverPageResponse;

import lombok.RequiredArgsConstructor;

/**
 * 用户公开主页中的公开行程查询接口。
 *
 * <p>控制器放在启动模块中，避免该接口因为只更新 trip-module、
 * 但未同步 match-module 控制器而缺失。实际查询仍由 MatchService 完成。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/trips/users")
public class TripPublicUserController {

    private final MatchService matchService;

    @GetMapping("/{userId}/public")
    public Result<TripDiscoverPageResponse> publicTripsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(matchService.getPublicTripsByUser(userId, page, size));
    }
}
