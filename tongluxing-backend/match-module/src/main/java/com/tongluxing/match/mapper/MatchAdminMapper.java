package com.tongluxing.match.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 运营后台“发现同行”监控查询。
 *
 * <p>返回 Map 是为了直接适配动态看板字段；所有统计都忽略逻辑删除记录。该 Mapper
 * 只读，不参与在线推荐事务。</p>
 */
@Mapper
public interface MatchAdminMapper {
    /**
     * 汇总活跃行程、有效推荐、平均分以及曝光到完成的累计事件数。
     *
     * @return 单行看板概览，字段别名即接口 JSON 字段名
     */
    @Select("""
        select
          (select count(*) from trip where deleted=0 and status in ('PUBLISHED','RUNNING','ONGOING')) activeTrips,
          (select count(*) from match_result where deleted=0 and result_status='VALID') recommendationCount,
          (select coalesce(round(avg(match_score),1),0) from match_result where deleted=0 and result_status='VALID') averageScore,
          (select count(*) from match_recommend_log where deleted=0 and action_type='IMPRESSION') impressions,
          (select count(*) from match_recommend_log where deleted=0 and action_type='CLICK') clicks,
          (select count(*) from match_recommend_log where deleted=0 and action_type='APPLY') applications,
          (select count(*) from match_recommend_log where deleted=0 and action_type='ACCEPT') accepts,
          (select count(*) from match_recommend_log where deleted=0 and action_type='START') starts,
          (select count(*) from match_recommend_log where deleted=0 and action_type='FINISH') finishes
        """)
    Map<String,Object> overview();

    /**
     * 查询最近计算的推荐记录。
     *
     * <p>同时关联源/目标行程以生成人类可读路线，按 calculated_at 倒序。</p>
     *
     * @param limit 最大返回记录数
     * @return 近期推荐明细
     */
    @Select("""
        select r.id matchId,r.source_trip_id sourceTripId,r.target_trip_id targetTripId,
               concat(s.start_name,' → ',s.end_name) sourceRoute,
               concat(t.start_name,' → ',t.end_name) targetRoute,
               r.match_score matchScore,r.departure_gap_minutes departureGapMinutes,
               r.result_status status,r.calculated_at calculatedAt
        from match_result r join trip s on s.id=r.source_trip_id join trip t on t.id=r.target_trip_id
        where r.deleted=0 order by r.calculated_at desc limit #{limit}
        """)
    List<Map<String,Object>> records(@Param("limit") int limit);

    /**
     * 按目标行程起终点聚合热门路线、平均分、点击和申请次数。
     *
     * <p>LEFT JOIN 保留尚无行为日志但已有推荐结果的路线。</p>
     */
    @Select("""
        select concat(t.start_name,' → ',t.end_name) routeName,count(*) recommendationCount,
               round(avg(r.match_score),1) averageScore,
               sum(case when l.action_type='CLICK' then 1 else 0 end) clicks,
               sum(case when l.action_type='APPLY' then 1 else 0 end) applications
        from match_result r join trip t on t.id=r.target_trip_id
        left join match_recommend_log l on l.target_trip_id=r.target_trip_id and l.deleted=0
        where r.deleted=0 and r.result_status='VALID'
        group by t.start_name,t.end_name order by recommendationCount desc limit #{limit}
        """)
    List<Map<String,Object>> popularRoutes(@Param("limit") int limit);

    /** 按固定业务顺序汇总发现同行场景的行为漏斗及去重用户数。 */
    @Select("""
        select action_type actionType,count(*) eventCount,count(distinct user_id) userCount
        from match_recommend_log where deleted=0 and scene='DISCOVER_COMPANION'
        group by action_type
        order by field(action_type,'IMPRESSION','CLICK','APPLY','ACCEPT','START','FINISH')
        """)
    List<Map<String,Object>> funnel();

    /**
     * 发现高曝光但零点击的目标行程，供运营检查封面、标题或推荐质量。
     *
     * <p>当前异常阈值为至少 20 次曝光，最多返回 20 条。</p>
     */
    @Select("""
        select target_trip_id targetTripId,count(*) impressions,
               sum(case when action_type='CLICK' then 1 else 0 end) clicks,
               '高曝光低点击' anomalyType
        from match_recommend_log where deleted=0 and scene='DISCOVER_COMPANION'
        group by target_trip_id having impressions >= 20 and clicks = 0 limit 20
        """)
    List<Map<String,Object>> anomalies();
}
