package com.tongluxing.match.service;

import com.tongluxing.match.vo.MatchRecommendationListResponse;
import com.tongluxing.match.vo.NearbyTeamListResponse;
import com.tongluxing.match.vo.NearbyTripListResponse;
import com.tongluxing.match.vo.MatchTripCardResponse;
import com.tongluxing.match.vo.MatchApplyResponse;
import com.tongluxing.match.dto.TripApplicationRequest;
import com.tongluxing.match.dto.TripSearchRequest;
import com.tongluxing.match.vo.TripSearchDetailResponse;
import com.tongluxing.match.vo.TripSearchPageResponse;
import com.tongluxing.match.vo.TripDiscoverPageResponse;
import com.tongluxing.match.vo.TripPublicDetailResponse;
import com.tongluxing.match.vo.TripConsultationResponse;

/**
 * 匹配模块业务服务接口。
 */
public interface MatchService {

    /** 发布行程后预生成并持久化推荐结果。 */
    void generateTripRecommendations(Long tripId);

    /**
     * 获取指定行程的推荐行程和推荐车队。
     *
     * @param tripId 当前行程 ID
     * @param limit 返回数量上限
     * @return 推荐结果
     */
    MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit);

    /** 查看单条匹配详情并记录点击。 */
    MatchTripCardResponse getRecommendation(Long matchId);

    /** 通过推荐结果提交目标车队入队申请。 */
    MatchApplyResponse apply(Long matchId, String message);

    /** 从地图附近招募列表直接申请加入目标行程对应的车队。 */
    MatchApplyResponse applyNearbyTrip(Long tripId, String message);

    /** 按地点、时间和容量条件搜索公开招募行程。 */
    TripSearchPageResponse searchTrips(TripSearchRequest request);

    /** 查询未入队用户可见的公开行程详情。 */
    TripSearchDetailResponse getSearchTripDetail(Long tripId);

    /** 从搜索结果申请加入行程对应车队。 */
    MatchApplyResponse applyToTrip(Long tripId, TripApplicationRequest request);

    /** 不要求用户先发布行程的公共发现信息流。 */
    TripDiscoverPageResponse discoverTrips(
            String keyword, String sort, Double latitude, Double longitude, Long referenceTripId,
            String startCity, String destination, String departureDateFrom, String departureDateTo,
            String vehicleType, Integer minimumRemainingSeats, Integer page, Integer size);

    /** 查询某位用户仍在公开展示的行程，供公开资料页使用。 */
    TripDiscoverPageResponse getPublicTripsByUser(Long userId, Integer page, Integer size);

    /** 发现信息流专用的公开详情聚合。 */
    TripPublicDetailResponse getPublicTripDetail(Long tripId);

    /** 收藏或取消收藏公开行程。 */
    Boolean favoriteTrip(Long tripId);

    Boolean unfavoriteTrip(Long tripId);

    /** 按关注/同行关系创建一次受控的行程咨询。 */
    TripConsultationResponse consultTrip(Long tripId, String content);

    /** 记录推荐关联行程的开始/结束事件。 */
    void recordTripLifecycle(Long tripId, String actionType);

    /** 记录推荐入队审核结果。 */
    void recordTeamApplication(Long applicantUserId, Long targetTripId, Long teamId, String status);

    /**
     * 查询附近公开行程。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param radiusMeters 查询半径，单位米
     * @param limit 返回数量上限
     * @return 附近行程列表
     */
    NearbyTripListResponse getNearbyTrips(String latitude, String longitude, Integer radiusMeters, Integer limit);

    /**
     * 查询附近公开活跃车队。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param radiusMeters 查询半径，单位米
     * @param limit 返回数量上限
     * @return 附近车队列表
     */
    NearbyTeamListResponse getNearbyTeams(String latitude, String longitude, Integer radiusMeters, Integer limit);
}
