package com.tongluxing.match.service;

import com.tongluxing.match.vo.MatchRecommendationListResponse;
import com.tongluxing.match.vo.NearbyTeamListResponse;
import com.tongluxing.match.vo.NearbyTripListResponse;
import com.tongluxing.match.vo.MatchTripCardResponse;
import com.tongluxing.match.vo.MatchApplyResponse;

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
