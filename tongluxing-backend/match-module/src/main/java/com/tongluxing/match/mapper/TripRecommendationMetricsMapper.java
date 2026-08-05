package com.tongluxing.match.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 推荐页热度与队长评分聚合查询。
 *
 * <p>一次批量查询完成报名人数、收藏数、队长评分和好评率聚合，避免候选列表逐条
 * 查询形成 N+1。评分汇总尚未建立时使用中性默认值：评分 5.0、好评率 1.0。</p>
 */
@Mapper
public interface TripRecommendationMetricsMapper {

    @Select({
            "<script>",
            "SELECT t.id AS tripId,",
            "       COUNT(DISTINCT CASE WHEN a.deleted=0 THEN a.applicant_user_id END) AS applicationCount,",
            "       COUNT(DISTINCT f.user_id) AS favoriteCount,",
            "       COALESCE(r.rating, 5.0) AS leaderRating,",
            "       COALESCE(r.positive_rate, 1.0) AS positiveRate",
            "FROM trip t",
            "LEFT JOIN team_join_application a ON a.trip_id=t.id AND a.deleted=0",
            "LEFT JOIN trip_favorite f ON f.trip_id=t.id",
            "LEFT JOIN trip_leader_rating_summary r ON r.leader_user_id=t.user_id AND r.deleted=0",
            "WHERE t.id IN",
            "<foreach collection='tripIds' item='tripId' open='(' separator=',' close=')'>",
            "#{tripId}",
            "</foreach>",
            "GROUP BY t.id, r.rating, r.positive_rate",
            "</script>"
    })
    List<TripRecommendationMetricRow> findByTripIds(@Param("tripIds") List<Long> tripIds);

    /** 推荐页计算所需的聚合指标。 */
    record TripRecommendationMetricRow(
            Long tripId,
            Integer applicationCount,
            Integer favoriteCount,
            Double leaderRating,
            Double positiveRate
    ) {
    }
}
