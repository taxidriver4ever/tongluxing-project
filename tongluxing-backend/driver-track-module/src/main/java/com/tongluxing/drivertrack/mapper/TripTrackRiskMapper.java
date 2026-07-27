package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 轨迹风险事件与汇总 Mapper。 */
@Mapper
public interface TripTrackRiskMapper {

    @Insert("""
            insert ignore into trip_track_summary(
              id, trip_id, primary_user_id, raw_distance_meters, filtered_distance_meters,
              approved_distance_meters, total_point_count, valid_point_count, invalid_point_count,
              location_gap_count, warning_count, hard_anomaly_count, risk_score, risk_level,
              settlement_status, review_reason, created_at, updated_at, deleted
            ) values (
              #{id}, #{tripId}, #{primaryUserId}, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 'LOW',
              'PENDING', null, #{now}, #{now}, 0
            )
            """)
    int ensureSummary(@Param("id") Long id,
                      @Param("tripId") Long tripId,
                      @Param("primaryUserId") Long primaryUserId,
                      @Param("now") LocalDateTime now);

    @Update("""
            update trip_track_summary
            set raw_distance_meters = raw_distance_meters + #{rawDistance},
                filtered_distance_meters = filtered_distance_meters + #{validDistance},
                total_point_count = total_point_count + 1,
                valid_point_count = valid_point_count + #{validPoint},
                invalid_point_count = invalid_point_count + #{invalidPoint},
                location_gap_count = location_gap_count + #{gapCount},
                warning_count = warning_count + #{warningCount},
                hard_anomaly_count = hard_anomaly_count + #{hardCount},
                risk_score = greatest(#{minimumRiskScore}, risk_score + #{riskScore}),
                review_reason = case when #{reviewReason} is not null then #{reviewReason} else review_reason end,
                updated_at = #{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int appendPoint(@Param("tripId") Long tripId,
                    @Param("rawDistance") int rawDistance,
                    @Param("validDistance") int validDistance,
                    @Param("validPoint") int validPoint,
                    @Param("invalidPoint") int invalidPoint,
                    @Param("gapCount") int gapCount,
                    @Param("warningCount") int warningCount,
                    @Param("hardCount") int hardCount,
                    @Param("riskScore") int riskScore,
                    @Param("minimumRiskScore") int minimumRiskScore,
                    @Param("reviewReason") String reviewReason,
                    @Param("now") LocalDateTime now);

    @Update("""
            update trip_track_summary
            set risk_level = case
                    when risk_score >= #{highRiskScore} then 'HIGH'
                    when risk_score >= #{mediumRiskScore} then 'MEDIUM'
                    else 'LOW' end,
                settlement_status = case
                    when risk_score >= #{mediumRiskScore} then 'MANUAL_REVIEW'
                    else settlement_status end,
                updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int refreshRiskLevel(@Param("tripId") Long tripId,
                         @Param("mediumRiskScore") int mediumRiskScore,
                         @Param("highRiskScore") int highRiskScore,
                         @Param("now") LocalDateTime now);

    @Insert("""
            insert into trip_track_anomaly(
              id, trip_id, user_id, previous_point_id, current_point_id, anomaly_type,
              risk_score, detail_json, occurred_at, created_at
            ) values (
              #{id}, #{tripId}, #{userId}, #{previousPointId}, #{currentPointId}, #{type},
              #{riskScore}, #{detailJson}, #{occurredAt}, #{createdAt}
            )
            """)
    int insertAnomaly(@Param("id") Long id,
                      @Param("tripId") Long tripId,
                      @Param("userId") Long userId,
                      @Param("previousPointId") Long previousPointId,
                      @Param("currentPointId") Long currentPointId,
                      @Param("type") String type,
                      @Param("riskScore") int riskScore,
                      @Param("detailJson") String detailJson,
                      @Param("occurredAt") LocalDateTime occurredAt,
                      @Param("createdAt") LocalDateTime createdAt);

    @Update("""
            update trip_track_summary
            set risk_score=greatest(risk_score, #{mediumRiskScore}),
                risk_level=case when risk_level='HIGH' then 'HIGH' else 'MEDIUM' end,
                settlement_status='MANUAL_REVIEW',
                review_reason=#{reason}, updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int markAtLeastMedium(@Param("tripId") Long tripId,
                          @Param("mediumRiskScore") int mediumRiskScore,
                          @Param("reason") String reason,
                          @Param("now") LocalDateTime now);

    @Select("""
            select coalesce(location_gap_count, 0)
            from trip_track_summary
            where trip_id=#{tripId} and deleted=0 limit 1
            """)
    int currentGapCount(Long tripId);


    @Select("""
            select count(*)
            from trip_track_anomaly
            where trip_id=#{tripId} and user_id=#{userId}
              and anomaly_type in ('FATAL_IMPOSSIBLE_SPEED','FATAL_REJECTED')
              and occurred_at >= #{since}
            """)
    int countFatalAnomaliesSince(@Param("tripId") Long tripId,
                                 @Param("userId") Long userId,
                                 @Param("since") LocalDateTime since);

    @Select("""
            select risk_score riskScore, risk_level riskLevel,
                   settlement_status settlementStatus, review_reason reviewReason,
                   raw_distance_meters rawDistanceMeters,
                   filtered_distance_meters filteredDistanceMeters,
                   approved_distance_meters approvedDistanceMeters,
                   total_point_count totalPointCount, valid_point_count validPointCount,
                   invalid_point_count invalidPointCount, location_gap_count locationGapCount,
                   warning_count warningCount, hard_anomaly_count hardAnomalyCount
            from trip_track_summary where trip_id=#{tripId} and deleted=0 limit 1
            """)
    Map<String, Object> findSummary(Long tripId);
}
