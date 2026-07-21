package com.tongluxing.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

import com.tongluxing.match.entity.MatchResult;

/**
 * 行程匹配结果 Mapper。
 */
@Mapper
public interface MatchResultMapper {

    /**
     * 新增或更新两条行程之间的匹配结果。
     */
    @Insert("""
            insert into match_result
                (id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                 match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                 score_detail_json, result_status, calculated_at, created_at, updated_at, deleted)
            values
                (#{id}, #{sourceTripId}, #{targetTripId}, #{sourceUserId}, #{targetUserId},
                 #{matchScore}, #{overlapRate}, #{distanceGapMeters}, #{departureGapMinutes},
                 #{scoreDetailJson}, #{resultStatus}, #{calculatedAt}, #{createdAt}, #{updatedAt}, 0)
            on duplicate key update
                 match_score = values(match_score),
                 overlap_rate = values(overlap_rate),
                 distance_gap_meters = values(distance_gap_meters),
                 departure_gap_minutes = values(departure_gap_minutes),
                 score_detail_json = values(score_detail_json),
                 result_status = values(result_status),
                 calculated_at = values(calculated_at),
                 updated_at = values(updated_at)
            """)
    void upsert(MatchResult result);

    @Select("""
            select id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                   match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                   score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
            from match_result
            where source_trip_id = #{tripId} and result_status = 'VALID' and deleted = 0
            order by match_score desc, departure_gap_minutes asc
            limit #{limit}
            """)
    List<MatchResult> findBySourceTrip(@Param("tripId") Long tripId, @Param("limit") Integer limit);

    @Select("""
            select id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                   match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                   score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
            from match_result where id = #{id} and deleted = 0 limit 1
            """)
    MatchResult findById(@Param("id") Long id);

    @Select("""
            select id, source_trip_id, target_trip_id, source_user_id, target_user_id,
                   match_score, overlap_rate, distance_gap_meters, departure_gap_minutes,
                   score_detail_json, result_status, calculated_at, created_at, updated_at, deleted
            from match_result
            where source_user_id=#{userId} and target_trip_id=#{targetTripId} and deleted=0
            order by calculated_at desc limit 1
            """)
    MatchResult findLatestByApplicant(@Param("userId") Long userId, @Param("targetTripId") Long targetTripId);
}
