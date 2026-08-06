package com.tongluxing.match.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 推荐页热度与队长评分聚合查询。
 *
 * <p>一次批量查询完成真实报名人数、收藏数、队长评分和好评率聚合，避免候选列表逐条
 * 查询形成 N+1。没有有效评价时评分、好评率和评价数均返回 0，不注入演示数据。</p>
 */
@Mapper
public interface TripRecommendationMetricsMapper {

    @Select({
            "<script>",
            "SELECT t.id AS tripId,",
            "       COUNT(DISTINCT CASE WHEN a.deleted=0 THEN a.applicant_user_id END) AS applicationCount,",
            "       COUNT(DISTINCT f.user_id) AS favoriteCount,",
            "       CASE WHEN COALESCE(r.rating_count, 0) > 0 THEN COALESCE(r.rating, 0.0) ELSE 0.0 END AS leaderRating,",
            "       CASE WHEN COALESCE(r.rating_count, 0) > 0 THEN COALESCE(r.positive_rate, 0.0) ELSE 0.0 END AS positiveRate,",
            "       COALESCE(r.rating_count, 0) AS ratingCount",
            "FROM trip t",
            "LEFT JOIN team_join_application a ON a.trip_id=t.id AND a.deleted=0",
            "LEFT JOIN trip_favorite f ON f.trip_id=t.id",
            "LEFT JOIN trip_leader_rating_summary r ON r.leader_user_id=t.user_id AND r.deleted=0",
            "WHERE t.id IN",
            "<foreach collection='tripIds' item='tripId' open='(' separator=',' close=')'>",
            "#{tripId}",
            "</foreach>",
            "GROUP BY t.id, r.rating, r.positive_rate, r.rating_count",
            "</script>"
    })
    List<TripRecommendationMetricRow> findByTripIds(@Param("tripIds") List<Long> tripIds);

    /** 推荐页计算所需的聚合指标。 */
    record TripRecommendationMetricRow(
            Long tripId,
            Integer applicationCount,
            Integer favoriteCount,
            Double leaderRating,
            Double positiveRate,
            Integer ratingCount
    ) {
    }
}
