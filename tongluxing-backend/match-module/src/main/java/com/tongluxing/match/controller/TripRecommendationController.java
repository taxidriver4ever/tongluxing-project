package com.tongluxing.match.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.TripRecommendPageResponse;

import lombok.RequiredArgsConstructor;

/**
 * App「行程」Tab 的推荐列表接口。
 *
 * <p>Nginx 对外统一增加 {@code /api} 前缀，因此该控制器的
 * {@code /trips/recommend} 对外地址正好是需求约定的
 * {@code GET /api/trips/recommend}。旧的 {@code /v1/trips/recommend}
 * 继续保留，避免已安装版本在升级期间立即失效。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/trips")
public class TripRecommendationController {

    private final MatchService matchService;

    /**
     * 按顺路率/热度、距离或时间返回统一过滤后的推荐分页。
     *
     * <p>{@code user_has_trip} 用于前端状态对齐，真正的推荐基准仍由服务端
     * 查询当前用户自己的有效行程，防止伪造参数改变推荐结果。</p>
     */
    @GetMapping("/recommend")
    public Result<TripRecommendPageResponse> recommend(
            @RequestParam(name = "sort_by", defaultValue = "match_rate") String sortBy,
            @RequestParam(name = "user_has_trip", required = false) Boolean userHasTrip,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(name = "page_size", defaultValue = "10") Integer pageSize) {
        return Result.success(matchService.recommendTrips(
                sortBy, userHasTrip, latitude, longitude, page, pageSize));
    }
}
