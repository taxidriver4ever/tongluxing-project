package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.TripTrackReviewSnapshot;

/**
 * 行程执行结算的只读轨迹统计与结算快照。
 *
 * <p>规划路线与实际轨迹严格分开：这里永远只读取驾驶端上传的原始轨迹点，
 * 不读取 trip_route 的推荐路线距离。</p>
 */
@Mapper
public interface TripExecutionSettlementMapper {

    @Insert("""
            insert ignore into trip_execution
              (id,trip_id,captain_user_id,status,planned_distance_m,started_at,
               created_at,updated_at,deleted)
            values
              (#{id},#{tripId},#{captainId},'ACTIVE',#{plannedDistance},#{now},
               #{now},#{now},0)
            """)
    int createExecution(
            @Param("id") Long id, @Param("tripId") Long tripId,
            @Param("captainId") Long captainId,
            @Param("plannedDistance") int plannedDistance,
            @Param("now") LocalDateTime now);

    @Select("select id from trip_execution where trip_id=#{tripId} and deleted=0 limit 1")
    Long executionId(Long tripId);

    @Insert("""
            insert ignore into trip_execution_member
              (id,execution_id,trip_id,user_id,member_role,member_status,ready_at,
               joined_execution_at,eligible_flag,created_at,updated_at,deleted)
            values
              (#{id},#{executionId},#{tripId},#{userId},#{role},'ACTIVE',#{now},
               #{now},1,#{now},#{now},0)
            """)
    int createExecutionMember(
            @Param("id") Long id, @Param("executionId") Long executionId,
            @Param("tripId") Long tripId, @Param("userId") Long userId,
            @Param("role") String role, @Param("now") LocalDateTime now);

    @Select("""
            select user_id from trip_execution_member
            where trip_id=#{tripId} and eligible_flag=1
              and member_status not in ('LEFT_EARLY','REMOVED','INELIGIBLE')
              and deleted=0
            """)
    List<Long> eligibleMemberIds(Long tripId);

    @Select("""
            select coalesce(
              (select coalesce(nullif(approved_distance_meters, 0), filtered_distance_meters)
               from trip_track_summary where trip_id=#{tripId} and deleted=0 limit 1),
              (select coalesce(sum(distance_from_prev), 0) from driver_track_record
               where trip_id=#{tripId} and driver_id=#{driverId} and deleted=0 and valid_point=1),
              0)
            """)
    int actualDistance(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select coalesce(risk_level, 'LOW')
            from trip_track_summary
            where trip_id=#{tripId} and deleted=0 limit 1
            """)
    String riskLevel(Long tripId);

    @Select("""
            select coalesce(risk_score, 0)
            from trip_track_summary
            where trip_id=#{tripId} and deleted=0 limit 1
            """)
    Integer riskScore(Long tripId);

    @Select("""
            select case when review_reason like '%有效定位点比例不足%' then 1 else 0 end
            from trip_track_summary
            where trip_id=#{tripId} and deleted=0 limit 1
            """)
    Integer coverageRiskApplied(Long tripId);

    @Update("""
            update trip_track_summary
            set risk_score=#{riskScore}, risk_level=#{riskLevel},
                settlement_status='MANUAL_REVIEW',
                review_reason=case when review_reason is null or review_reason='' then #{reason}
                    else concat(review_reason, '；', #{reason}) end, updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
              and settlement_status not in ('SETTLED','APPROVED','REJECTED')
            """)
    int markCoverageRisk(@Param("tripId") Long tripId,
                         @Param("riskScore") int riskScore,
                         @Param("riskLevel") String riskLevel,
                         @Param("reason") String reason,
                         @Param("now") LocalDateTime now);

    @Select("""
            select coalesce(sum(distance_from_prev), 0)
            from driver_track_record
            where trip_id=#{tripId} and driver_id<>#{captainId}
              and valid_point=1 and deleted=0
            group by driver_id
            having count(*) > 1
            order by coalesce(sum(distance_from_prev), 0)
            """)
    List<Integer> validMemberDistances(@Param("tripId") Long tripId,
                                       @Param("captainId") Long captainId);

    @Select("""
            select settlement_status
            from trip_track_summary
            where trip_id=#{tripId} and deleted=0 limit 1
            """)
    String trackSettlementStatus(Long tripId);

    @Select("""
            select count(*)
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
            """)
    int totalPoints(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select count(*)
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and valid_point = 1
            """)
    int validPoints(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select case when count(*) = #{minPoints}
                    and timestampdiff(second, min(record_time), max(record_time)) >= #{minDurationSeconds}
                  then 1 else 0 end
            from (
              select record_time
              from driver_track_record
              where trip_id=#{tripId} and driver_id=#{driverId} and deleted=0
                and valid_point=1
                and point_status not in ('LOCATION_GAP','RECOVERY_SEGMENT_START')
                and st_distance_sphere(point(longitude,latitude),point(#{lng},#{lat})) <= #{radiusMeters}
              order by record_time desc
              limit #{minPoints}
            ) destination_evidence
            """)
    int hasDestinationArrival(
            @Param("tripId") Long tripId,
            @Param("driverId") Long driverId,
            @Param("lng") java.math.BigDecimal lng,
            @Param("lat") java.math.BigDecimal lat,
            @Param("radiusMeters") int radiusMeters,
            @Param("minPoints") int minPoints,
            @Param("minDurationSeconds") int minDurationSeconds);

    @Select("""
            select count(*)
            from trip_track_anomaly a
            where a.trip_id=#{tripId} and a.user_id=#{driverId}
              and a.anomaly_type in ('FATAL_IMPOSSIBLE_SPEED','FATAL_REJECTED')
              and a.occurred_at >= date_sub(
                coalesce((
                  select max(record_time)
                  from driver_track_record
                  where trip_id=#{tripId} and driver_id=#{driverId} and deleted=0
                    and valid_point=1
                    and st_distance_sphere(point(longitude,latitude),point(#{lng},#{lat})) <= #{radiusMeters}
                ), now()), interval #{cooldownSeconds} second)
            """)
    int recentFatalAnomaliesAtDestination(
            @Param("tripId") Long tripId,
            @Param("driverId") Long driverId,
            @Param("lng") java.math.BigDecimal lng,
            @Param("lat") java.math.BigDecimal lat,
            @Param("radiusMeters") int radiusMeters,
            @Param("cooldownSeconds") int cooldownSeconds);


    @Select("""
            select count(*) from trip_waypoint
            where trip_id=#{tripId} and waypoint_type='REQUIRED' and deleted=0
            """)
    int requiredWaypointCount(Long tripId);

    @Select("""
            select count(distinct w.id)
            from trip_waypoint w
            join driver_track_distance_record d
              on d.trip_id=w.trip_id and d.driver_id=#{driverId}
             and d.settle_type='WAYPOINT'
             and d.settle_key=concat(w.trip_id,':',#{driverId},':TRIP_WAYPOINT:',w.id)
             and d.deleted=0
            where w.trip_id=#{tripId} and w.waypoint_type='REQUIRED' and w.deleted=0
            """)
    int arrivedRequiredWaypointCount(
            @Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select count(*)
            from trip_mileage_settlement
            where trip_id = #{tripId} and deleted = 0
            """)
    int settlementExists(Long tripId);

    @Insert("""
            insert into trip_mileage_settlement
              (id, trip_id, raw_gps_distance_m, matched_road_distance_m,
               estimated_gap_distance_m, settlement_distance_m, track_coverage_rate,
               estimated_ratio, quality_status, settlement_status, growth_value,
               reason, settled_at, created_at, updated_at, deleted)
            values
              (#{id}, #{tripId}, #{distance}, #{distance}, 0, #{distance}, #{coverage},
               0, #{quality}, #{status}, #{growth}, #{reason}, #{now}, #{now}, #{now}, 0)
            """)
    int insertSettlement(
            @Param("id") Long id,
            @Param("tripId") Long tripId,
            @Param("distance") int distance,
            @Param("coverage") int coverage,
            @Param("quality") String quality,
            @Param("status") String status,
            @Param("growth") int growth,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now);

    @Update("""
            update trip_execution
            set status=#{status}, raw_gps_distance_m=#{distance},
                matched_road_distance_m=#{distance}, settlement_distance_m=#{distance},
                ended_at=#{now}, updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int finishExecution(
            @Param("tripId") Long tripId,
            @Param("status") String status,
            @Param("distance") int distance,
            @Param("now") LocalDateTime now);
    /** 锁定轨迹汇总，供管理员人工审核。 */
    @Select("""
            select trip_id, primary_user_id, raw_distance_meters, filtered_distance_meters,
                   approved_distance_meters, total_point_count, valid_point_count, invalid_point_count,
                   location_gap_count, warning_count, hard_anomaly_count, risk_score, risk_level,
                   settlement_status, review_reason, reviewer_id, reviewed_at
            from trip_track_summary
            where trip_id=#{tripId} and deleted=0
            for update
            """)
    TripTrackReviewSnapshot findTrackReviewForUpdate(Long tripId);

    /** 写入人工审核结果，原始轨迹点不可修改。 */
    @Update("""
            update trip_track_summary
            set approved_distance_meters=#{approvedDistance},
                risk_level=#{riskLevel}, settlement_status=#{status},
                review_reason=#{reason}, reviewer_id=#{reviewerId}, reviewed_at=#{now},
                updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int updateTrackReview(@Param("tripId") Long tripId,
                          @Param("approvedDistance") int approvedDistance,
                          @Param("riskLevel") String riskLevel,
                          @Param("status") String status,
                          @Param("reason") String reason,
                          @Param("reviewerId") Long reviewerId,
                          @Param("now") LocalDateTime now);

    /** 更新已经生成的结算快照。 */
    @Update("""
            update trip_mileage_settlement
            set settlement_distance_m=#{distance}, matched_road_distance_m=#{distance},
                quality_status=#{quality}, settlement_status=#{status}, growth_value=#{growth},
                reason=#{reason}, settled_at=#{now}, updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int updateMileageSettlementReview(@Param("tripId") Long tripId,
                                      @Param("distance") int distance,
                                      @Param("quality") String quality,
                                      @Param("status") String status,
                                      @Param("growth") int growth,
                                      @Param("reason") String reason,
                                      @Param("now") LocalDateTime now);

    /** 管理员审核通过后将已结束行程推进为 SETTLED。 */
    @Update("""
            update trip
            set status='SETTLED', updated_at=#{now}
            where id=#{tripId} and status in ('FINISHED','ENDED','SETTLED') and deleted=0
            """)
    int settleTripByAdmin(@Param("tripId") Long tripId, @Param("now") LocalDateTime now);

    /** 管理员驳回成长值时保留已结束状态。 */
    @Update("""
            update trip_execution
            set status=#{status}, settlement_distance_m=#{distance}, updated_at=#{now}
            where trip_id=#{tripId} and deleted=0
            """)
    int reviewExecution(@Param("tripId") Long tripId,
                        @Param("status") String status,
                        @Param("distance") int distance,
                        @Param("now") LocalDateTime now);

}
