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
import com.tongluxing.match.dto.TripSearchHistoryRequest;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.vo.MatchApplyResponse;
import com.tongluxing.match.vo.TripSearchDetailResponse;
import com.tongluxing.match.vo.TripSearchPageResponse;
import com.tongluxing.match.vo.TripSearchHistoryResponse;
import com.tongluxing.match.vo.TripDiscoverPageResponse;
import com.tongluxing.match.vo.TripPublicDetailResponse;
import com.tongluxing.match.vo.TripConsultationResponse;
import com.tongluxing.match.vo.TripRecommendPageResponse;
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

    /** 负责搜索、发现、详情、收藏、咨询以及申请编排。 */
    private final MatchService matchService;
    /** 负责申请列表与队长审核；成员关系的最终状态归 team-module 所有。 */
    private final TeamService teamService;

    /**
     * 使用结构化起终点、时间窗口和车辆条件执行高级行程搜索。
     *
     * @param request 完整搜索条件
     * @return 过滤、评分和排序后的分页结果
     */
    @PostMapping("/search")
    public Result<TripSearchPageResponse> search(@Valid @RequestBody TripSearchRequest request) {
        // @Valid 校验单字段边界，跨字段时间顺序和地点重复由 MatchService 校验。
        return Result.success(matchService.searchTrips(request));
    }

    /** 查询行程搜索页历史记录。 */
    @GetMapping("/search-history")
    public Result<List<TripSearchHistoryResponse>> searchHistory(
            @RequestParam(defaultValue = "12") Integer limit) {
        return Result.success(matchService.getTripSearchHistory(limit));
    }

    /** 记录一次有效行程搜索；相同关键词和类型会更新时间而不会重复堆积。 */
    @PostMapping("/search-history")
    public Result<TripSearchHistoryResponse> recordSearchHistory(
            @RequestBody TripSearchHistoryRequest request) {
        return Result.success(matchService.recordTripSearchHistory(
                request == null ? null : request.keyword(),
                request == null ? null : request.searchType()));
    }

    /** 删除单条行程搜索历史。 */
    @DeleteMapping("/search-history/{historyId}")
    public Result<Boolean> deleteSearchHistory(@PathVariable Long historyId) {
        return Result.success(matchService.deleteTripSearchHistory(historyId));
    }

    /** 清空当前用户全部行程搜索历史。 */
    @DeleteMapping("/search-history")
    public Result<Integer> clearSearchHistory() {
        return Result.success(matchService.clearTripSearchHistory());
    }

    /**
     * 查询公共发现信息流。
     *
     * <p>支持全文/起点/终点/路线/行程号搜索、日期和车辆过滤、基准行程顺路评分、
     * 附近排序及刷新种子轮换。分页最大值由 Service 限制。</p>
     *
     * @param keyword 可选搜索词
     * @param searchType ALL、DESTINATION、ORIGIN、ROUTE 或 TRIP_NUMBER
     * @param sort RECOMMENDED、NEARBY、DEPARTURE_TIME 或 ROUTE_MATCH
     * @param latitude 当前纬度
     * @param longitude 当前经度
     * @param referenceTripId 当前用户的可选基准行程
     * @param startCity 起点城市过滤
     * @param destination 终点过滤
     * @param departureDateFrom 最早出发日期，格式 yyyy-MM-dd
     * @param departureDateTo 最晚出发日期
     * @param vehicleType 车辆类型过滤
     * @param minimumRemainingSeats 最少剩余名额
     * @param page 页码
     * @param size 页大小
     * @param refreshSeed 推荐轮换种子
     * @return 公共发现分页结果
     */
    /**
     * 查询 App「行程-推荐」分页列表。
     *
     * <p>sort_by 支持 match_rate、distance、time。用户没有自有行程时，
     * match_rate 会由服务端自动切换为 heat。user_has_trip 仅用于前端状态对齐，
     * 服务端仍以数据库中的真实自有行程为准。</p>
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
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) Long refreshSeed) {
        // 参数较多但全部是可选筛选项；归一化、组合过滤和评分只在 Service 实现一次。
        return Result.success(matchService.discoverTrips(keyword, searchType, sort, latitude, longitude, referenceTripId,
                startCity, destination, departureDateFrom, departureDateTo, vehicleType,
                minimumRemainingSeats, page, size, refreshSeed));
    }

    /** 查询指定用户仍处于公开招募状态的行程，供公开资料页使用。 */
    @GetMapping("/users/{userId}/public")
    public Result<TripDiscoverPageResponse> publicTripsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        // 查看他人时 Service 会复用用户公开主页权限，避免绕过 profileVisibility。
        return Result.success(matchService.getPublicTripsByUser(userId, page, size));
    }

    /**
     * 查询发现页公开详情；响应不包含聊天记录、手机号或完整车辆敏感信息。
     *
     * @param tripId 行程 ID
     * @return 服务端派生操作权限的公开详情
     */
    @GetMapping("/{tripId}/public-detail")
    public Result<TripPublicDetailResponse> publicDetail(@PathVariable Long tripId) {
        return Result.success(matchService.getPublicTripDetail(tripId));
    }

    /** 查询当前用户收藏的行程。 */
    @GetMapping("/favorites")
    public Result<TripDiscoverPageResponse> favorites(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(matchService.getFavoriteTrips(page, size));
    }

    /** 收藏一条仍在公开招募的行程；重复收藏按幂等成功处理。 */
    @PostMapping("/{tripId}/favorite")
    public Result<Boolean> favorite(@PathVariable Long tripId) {
        return Result.success(matchService.favoriteTrip(tripId));
    }

    /** 取消当前用户的行程收藏；收藏不存在时仍返回 false。 */
    @DeleteMapping("/{tripId}/favorite")
    public Result<Boolean> unfavorite(@PathVariable Long tripId) {
        return Result.success(matchService.unfavoriteTrip(tripId));
    }

    /**
     * 发起行程咨询。
     *
     * <p>已入队或互关用户可以直接沟通；单向关注用户会创建待处理咨询请求。</p>
     */
    @PostMapping("/{tripId}/consultations")
    public Result<TripConsultationResponse> consult(@PathVariable Long tripId,
                                                    @RequestBody(required = false) ConsultationBody body) {
        return Result.success(matchService.consultTrip(tripId, body == null ? null : body.content()));
    }

    /**
     * 从推荐卡片向队长发送固定问候。
     *
     * <p>该接口不要求先关注队长，但同一用户针对同一行程只能存在一条待处理问候，
     * 防止推荐列表被用于重复骚扰。</p>
     */
    @PostMapping("/{tripId}/greetings")
    public Result<TripConsultationResponse> greet(@PathVariable Long tripId) {
        return Result.success(matchService.greetTrip(tripId));
    }

    /**
     * 通用 GET /v1/trips/{tripId} 已由 trip-module 提供。
     * 此接口补充发现同行所需的公开队长和车队容量信息，且不暴露群聊内容。
     */
    @GetMapping("/{tripId}/discovery-detail")
    public Result<TripSearchDetailResponse> detail(@PathVariable Long tripId) {
        return Result.success(matchService.getSearchTripDetail(tripId));
    }

    /** 从搜索详情向行程关联车队提交结构化入队申请。 */
    @PostMapping("/{tripId}/applications")
    public Result<MatchApplyResponse> apply(@PathVariable Long tripId,
                                            @Valid @RequestBody TripApplicationRequest request) {
        return Result.success(matchService.applyToTrip(tripId, request));
    }

    /** 查询当前登录用户提交过的车队申请。 */
    @GetMapping("/applications/my")
    public Result<List<TeamApplicationResponse>> myApplications() {
        return Result.success(teamService.getMyApplications());
    }

    /** 查询指定行程收到的申请；队长权限由 TeamService 校验。 */
    @GetMapping("/{tripId}/applications")
    public Result<List<TeamApplicationResponse>> tripApplications(@PathVariable Long tripId) {
        return Result.success(teamService.getTripApplications(tripId));
    }

    /** 队长批准申请，成员加入和聊天权限由 TeamService 原子处理。 */
    @PostMapping("/applications/{applicationId}/approve")
    public Result<TeamApplicationResponse> approve(@PathVariable Long applicationId,
                                                    @RequestBody(required = false) ReviewBody body) {
        return Result.success(teamService.review(applicationId,
                new ReviewTeamApplicationRequest("APPROVE", body == null ? null : body.reason())));
    }

    /** 队长拒绝申请，可携带可选审核原因。 */
    @PostMapping("/applications/{applicationId}/reject")
    public Result<TeamApplicationResponse> reject(@PathVariable Long applicationId,
                                                   @RequestBody(required = false) ReviewBody body) {
        return Result.success(teamService.review(applicationId,
                new ReviewTeamApplicationRequest("REJECT", body == null ? null : body.reason())));
    }

    /**
     * 申请审核请求体。
     *
     * @param reason 队长填写的可选审核原因
     */
    public record ReviewBody(String reason) {
    }

    /**
     * 咨询请求体。
     *
     * @param content 咨询内容；为空时使用默认问候，最长 500 字
     */
    public record ConsultationBody(String content) {
    }
}
