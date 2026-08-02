package com.tongluxing.match.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.MatchRecommendationListResponse;
import com.tongluxing.match.vo.NearbyTeamListResponse;
import com.tongluxing.match.vo.NearbyTripListResponse;
import com.tongluxing.match.vo.MatchTripCardResponse;
import com.tongluxing.match.vo.MatchApplyResponse;
import java.util.Map;

import lombok.RequiredArgsConstructor;

/**
 * 匹配模块接口控制器，提供行程推荐和附近行程/车队查询能力。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/matches")
public class MatchController {

    /** 推荐、附近查询和申请的统一业务入口。 */
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
        // Service 会校验该行程属于当前用户，并限制最终返回条数。
        return Result.success(matchService.getTripRecommendations(tripId, limit));
    }

    /**
     * 为历史行程或规则变更后手动刷新一次推荐；正常发布链路会自动调用。
     *
     * @param tripId 当前用户拥有的源行程 ID
     * @param limit 重算后最多返回的推荐数
     * @return 最新持久化推荐列表
     */
    @PostMapping("/trips/{tripId}/generate")
    public Result<MatchRecommendationListResponse> generate(@PathVariable Long tripId,
                                                             @RequestParam(defaultValue = "20") Integer limit) {
        // 先重算并 upsert 持久化结果，再从数据库读取同一套排序后的推荐。
        matchService.generateTripRecommendations(tripId);
        return Result.success(matchService.getTripRecommendations(tripId, limit));
    }

    /**
     * 查询单条推荐详情，并在成功读取后记录一次 CLICK 漏斗事件。
     *
     * @param matchId 推荐结果 ID
     * @return 仍有效的目标行程卡片
     */
    @GetMapping("/recommendations/{matchId}")
    public Result<MatchTripCardResponse> getRecommendation(@PathVariable Long matchId) {
        return Result.success(matchService.getRecommendation(matchId));
    }

    /**
     * 通过一条属于当前用户的推荐记录申请加入目标行程车队。
     *
     * @param matchId 推荐结果 ID
     * @param body 可选请求体，仅读取 message
     * @return 待审核申请结果
     */
    @PostMapping("/recommendations/{matchId}/apply")
    public Result<MatchApplyResponse> apply(@PathVariable Long matchId,
                                            @RequestBody(required = false) Map<String, String> body) {
        // 请求体可省略；Service 会为缺失 message 使用明确的场景默认文案。
        return Result.success(matchService.apply(matchId, body == null ? null : body.get("message")));
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
        // 字符串坐标的格式、查询半径和数量边界统一在 Service 校验。
        return Result.success(matchService.getNearbyTrips(latitude, longitude, radiusMeters, limit));
    }

    /**
     * 从地图上的附近招募卡片直接提交入队申请。
     *
     * @param tripId 目标行程 ID
     * @param body 可选申请留言
     * @return 待审核申请结果
     */
    @PostMapping("/nearby-trips/{tripId}/apply")
    public Result<MatchApplyResponse> applyNearbyTrip(@PathVariable Long tripId,
                                                       @RequestBody(required = false) Map<String, String> body) {
        // 附近场景没有源行程，Service 会用特殊 requestId 保留申请来源审计。
        return Result.success(matchService.applyNearbyTrip(tripId, body == null ? null : body.get("message")));
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
        // 当前实现先验证坐标，再通过跨模块端口读取公开活跃车队。
        return Result.success(matchService.getNearbyTeams(latitude, longitude, radiusMeters, limit));
    }
}
