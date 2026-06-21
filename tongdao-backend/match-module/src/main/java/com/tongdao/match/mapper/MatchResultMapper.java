package com.tongdao.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import com.tongdao.match.entity.MatchResult;

@Mapper
public interface MatchResultMapper {

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
}
