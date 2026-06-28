package com.tongdao.match.service;

import com.tongdao.match.vo.MatchRecommendationListResponse;
import com.tongdao.match.vo.NearbyTeamListResponse;
import com.tongdao.match.vo.NearbyTripListResponse;

/**
 * 匹配模块业务服务接口。
 */
public interface MatchService {

    /**
     * 获取指定行程的推荐行程和推荐车队。
     *
     * @param tripId 当前行程 ID
     * @param limit 返回数量上限
     * @return 推荐结果
     */
    MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit);

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
