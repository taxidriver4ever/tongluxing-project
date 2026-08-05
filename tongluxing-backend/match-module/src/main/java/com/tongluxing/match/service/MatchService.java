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
import com.tongluxing.match.vo.TripRecommendPageResponse;
import com.tongluxing.match.vo.TripSearchHistoryResponse;

import java.util.List;

/**
 * 匹配模块业务服务接口。
 *
 * <p>公开方法统一负责登录用户识别、资源归属与公开性校验，并通过跨模块端口读取
 * 行程和车队。推荐分数只用于排序，真正的成员关系变更委托 TeamService 完成。</p>
 */
public interface MatchService {

    /**
     * 发布行程后预生成并持久化推荐结果。
     *
     * @param tripId 作为推荐基准的源行程 ID
     */
    void generateTripRecommendations(Long tripId);

    /**
     * 获取指定行程的推荐行程和推荐车队。
     *
     * @param tripId 当前行程 ID
     * @param limit 返回数量上限
     * @return 推荐结果
     */
    MatchRecommendationListResponse getTripRecommendations(Long tripId, Integer limit);

    /**
     * 查看单条匹配详情并记录点击。
     *
     * @param matchId 持久化推荐结果 ID
     * @return 当前仍有效且可匹配的目标行程卡片
     */
    MatchTripCardResponse getRecommendation(Long matchId);

    /**
     * 通过推荐结果提交目标车队入队申请。
     *
     * @param matchId 必须属于当前用户源行程的推荐 ID
     * @param message 可选申请说明
     * @return team-module 创建的待审核申请
     */
    MatchApplyResponse apply(Long matchId, String message);

    /**
     * 从地图附近招募列表直接申请加入目标行程对应的车队。
     *
     * @param tripId 目标行程 ID
     * @param message 可选申请说明
     * @return 待审核申请结果
     */
    MatchApplyResponse applyNearbyTrip(Long tripId, String message);

    /**
     * 按地点、时间、途经点、车辆认证和容量条件搜索公开招募行程。
     *
     * @param request 结构化搜索条件
     * @return 内存过滤评分后的分页结果
     */
    TripSearchPageResponse searchTrips(TripSearchRequest request);

    /**
     * 查询未入队用户可见的公开行程详情，不包含聊天与敏感成员资料。
     *
     * @param tripId 目标行程 ID
     * @return 搜索场景详情
     */
    TripSearchDetailResponse getSearchTripDetail(Long tripId);

    /**
     * 从搜索结果申请加入行程对应车队，并保存自驾/车辆等结构化信息。
     *
     * @param tripId 目标行程 ID
     * @param request 留言、自驾标志与申请车辆
     * @return 待审核申请结果
     */
    MatchApplyResponse applyToTrip(Long tripId, TripApplicationRequest request);

    /**
     * 不要求用户先发布行程的公共发现信息流。
     *
     * <p>referenceTripId 非空时必须属于当前用户，并改用路线、终点、时间和途经点
     * 相似度计算顺路分；否则使用容量、发起人活跃度、认证和距离生成发现分。</p>
     *
     * @param keyword 可选搜索词
     * @param searchType 搜索字段类型
     * @param sort 排序模式
     * @param latitude 当前纬度
     * @param longitude 当前经度
     * @param referenceTripId 当前用户的基准行程 ID
     * @param startCity 起点过滤
     * @param destination 终点过滤
     * @param departureDateFrom 最早出发日期
     * @param departureDateTo 最晚出发日期
     * @param vehicleType 车辆类型
     * @param minimumRemainingSeats 最少剩余名额
     * @param page 页码
     * @param size 页大小
     * @param refreshSeed 推荐轮换种子
     * @return 发现页分页结果
     */
    TripDiscoverPageResponse discoverTrips(
            String keyword, String searchType, String sort, Double latitude, Double longitude, Long referenceTripId,
            String startCity, String destination, String departureDateFrom, String departureDateTo,
            String vehicleType, Integer minimumRemainingSeats, Integer page, Integer size, Long refreshSeed);

    /**
     * 查询 App「行程-推荐」列表。
     *
     * <p>服务端自行判断用户是否存在自有基准行程；userHasTrip 只用于前后端状态
     * 对齐，不作为算法真值。有行程时第一排序为顺路率，无行程时自动切换热度。</p>
     */
    TripRecommendPageResponse recommendTrips(
            String sortBy, Boolean userHasTrip, Double latitude, Double longitude,
            Integer page, Integer pageSize);

    /**
     * 向推荐行程队长发送一次固定问候。
     *
     * @param tripId 目标行程 ID
     * @return 问候请求状态
     */
    TripConsultationResponse greetTrip(Long tripId);

    /**
     * 查询某位用户仍在公开招募且未过期的行程，供公开资料页使用。
     *
     * @param userId 目标用户 ID
     * @param page 页码
     * @param size 页大小
     * @return 公开行程分页结果
     */
    TripDiscoverPageResponse getPublicTripsByUser(Long userId, Integer page, Integer size);

    /**
     * 发现信息流专用公开详情，包含权限派生后的咨询、申请和收藏状态。
     *
     * @param tripId 目标行程 ID
     * @return 公开详情聚合
     */
    TripPublicDetailResponse getPublicTripDetail(Long tripId);


    /** 查询当前用户收藏的行程分页。 */
    TripDiscoverPageResponse getFavoriteTrips(Integer page, Integer size);

    /**
     * 收藏公开招募行程；重复调用保持已收藏状态。
     *
     * @param tripId 行程 ID
     * @return 固定 true，表示最终已收藏
     */
    Boolean favoriteTrip(Long tripId);

    /**
     * 取消收藏；关系不存在时仍返回未收藏状态。
     *
     * @param tripId 行程 ID
     * @return 固定 false，表示最终未收藏
     */
    Boolean unfavoriteTrip(Long tripId);

    /**
     * 按关注/互关/同行关系创建一次受控的行程咨询。
     *
     * @param tripId 目标行程 ID
     * @param content 可选咨询正文
     * @return 待处理或允许直接沟通的状态
     */
    TripConsultationResponse consultTrip(Long tripId, String content);


    /** 查询当前用户最近的行程搜索历史。 */
    List<TripSearchHistoryResponse> getTripSearchHistory(Integer limit);

    /** 按关键词与搜索类型幂等记录搜索历史。 */
    TripSearchHistoryResponse recordTripSearchHistory(String keyword, String searchType);

    /** 删除一条属于当前用户的搜索历史。 */
    Boolean deleteTripSearchHistory(Long historyId);

    /** 清空当前用户全部行程搜索历史。 */
    Integer clearTripSearchHistory();

    /**
     * 记录推荐关联行程的 START/FINISH 事件，其他动作类型会被忽略。
     *
     * @param tripId 生命周期发生变化的行程 ID
     * @param actionType START 或 FINISH
     */
    void recordTripLifecycle(Long tripId, String actionType);

    /**
     * 将 TeamService 的 APPROVED/REJECTED 结果关联回最近一次推荐申请漏斗。
     *
     * @param applicantUserId 申请人用户 ID
     * @param targetTripId 目标行程 ID
     * @param teamId 目标车队 ID
     * @param status 审核状态
     */
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
