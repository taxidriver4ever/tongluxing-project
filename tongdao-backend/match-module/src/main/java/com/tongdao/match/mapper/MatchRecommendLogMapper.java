package com.tongdao.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 推荐曝光与行为日志 Mapper。
 */
@Mapper
public interface MatchRecommendLogMapper {

    /**
     * 写入一次推荐场景下的用户行为日志。
     */
    @Insert("""
            insert into match_recommend_log
                (id, user_id, trip_id, target_trip_id, target_team_id, scene, action_type,
                 request_id, extra_json, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{tripId}, #{targetTripId}, #{targetTeamId}, #{scene}, #{actionType},
                 #{requestId}, #{extraJson}, now(), now(), 0)
            """)
    void insert(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("tripId") Long tripId,
                @Param("targetTripId") Long targetTripId,
                @Param("targetTeamId") Long targetTeamId,
                @Param("scene") String scene,
                @Param("actionType") String actionType,
                @Param("requestId") String requestId,
                @Param("extraJson") String extraJson);
}
