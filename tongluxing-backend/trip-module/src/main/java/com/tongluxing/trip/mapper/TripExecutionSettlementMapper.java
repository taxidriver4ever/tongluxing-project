package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            select coalesce(sum(distance_from_prev), 0)
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and valid_point = 1
            """)
    int actualDistance(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

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
            select case when count(*) >= 3
                    and timestampdiff(second, min(record_time), max(record_time)) >= 30
                  then 1 else 0 end
            from driver_track_record
            where trip_id=#{tripId} and driver_id=#{driverId} and deleted=0
              and record_time >= date_sub(now(), interval 10 minute)
              and valid_point = 1
              and st_distance_sphere(point(longitude,latitude),point(#{lng},#{lat})) <= 100
            """)
    int hasDestinationArrival(
            @Param("tripId") Long tripId,
            @Param("driverId") Long driverId,
            @Param("lng") java.math.BigDecimal lng,
            @Param("lat") java.math.BigDecimal lat);

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
}
