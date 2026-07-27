package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.drivertrack.entity.DriverTrackRecord;

@Mapper
public interface TripExecutionTrackMapper {
    @Insert("""
            insert ignore into trip_execution
              (id,trip_id,captain_user_id,status,planned_distance_m,started_at,
               created_at,updated_at,deleted)
            values
              (#{id},#{tripId},#{captainId},'ACTIVE',#{plannedDistance},#{now},
               #{now},#{now},0)
            """)
    int ensureExecution(@Param("id") Long id, @Param("tripId") Long tripId,
                        @Param("captainId") Long captainId,
                        @Param("plannedDistance") int plannedDistance,
                        @Param("now") LocalDateTime now);

    @Select("""
            select id from trip_execution
            where trip_id=#{tripId} and deleted=0 limit 1
            """)
    Long findExecutionId(Long tripId);

    @Insert("""
            insert ignore into trip_execution_member
              (id,execution_id,trip_id,user_id,member_role,member_status,
               ready_at,joined_execution_at,eligible_flag,created_at,updated_at,deleted)
            values
              (#{id},#{executionId},#{tripId},#{userId},#{role},'ACTIVE',
               #{now},#{now},1,#{now},#{now},0)
            """)
    int ensureMember(@Param("id") Long id, @Param("executionId") Long executionId,
                     @Param("tripId") Long tripId, @Param("userId") Long userId,
                     @Param("role") String role, @Param("now") LocalDateTime now);

    @Insert("""
            insert ignore into trip_track_point
              (id,execution_id,trip_id,user_id,device_id,sequence_no,longitude,latitude,altitude,
               accuracy,speed,bearing,provider,app_state,battery_level,located_at,client_send_time,
               server_receive_time,mock_location,point_status,valid_point,risk_score,risk_flags,
               reject_reason,calculated_speed_kmh,raw_distance_from_previous_m,
               distance_from_previous_m,created_at,deleted)
            values
              (#{point.id},#{executionId},#{point.tripId},#{point.driverId},
               #{point.deviceId},#{point.sequenceNo},#{point.longitude},#{point.latitude},#{point.altitude},
               #{point.accuracy},#{point.speed},#{point.direction},#{point.provider},#{point.appState},
               #{point.batteryLevel},#{point.recordTime},#{point.clientSendTime},#{point.serverReceiveTime},
               #{point.mockLocation},#{point.pointStatus},#{point.validPoint},#{point.riskScore},
               #{point.riskFlags},#{point.rejectReason},#{point.calculatedSpeedKmh},
               #{rawDistance},#{point.distanceFromPrev},#{point.createdAt},0)
            """)
    int insertPoint(@Param("executionId") Long executionId,
                    @Param("point") DriverTrackRecord point,
                    @Param("rawDistance") int rawDistance);

    @Update("""
            update trip_execution
            set raw_gps_distance_m=raw_gps_distance_m+#{rawDistance},
                matched_road_distance_m=matched_road_distance_m+#{validDistance},
                settlement_distance_m=matched_road_distance_m+#{validDistance}
                    +estimated_gap_distance_m,
                updated_at=#{now}
            where id=#{executionId} and deleted=0
            """)
    int appendDistance(@Param("executionId") Long executionId,
                       @Param("rawDistance") int rawDistance,
                       @Param("validDistance") int validDistance,
                       @Param("now") LocalDateTime now);

    @Insert("""
            insert ignore into trip_waypoint_arrival
              (id,execution_id,trip_id,waypoint_id,arrival_type,user_id,
               first_inside_at,confirmed_at,evidence_point_count,distance_m,created_at,deleted)
            values
              (#{id},#{executionId},#{tripId},#{waypointId},'WAYPOINT',#{userId},
               #{firstInsideAt},#{confirmedAt},#{evidenceCount},#{distance},#{confirmedAt},0)
            """)
    int insertWaypointArrival(
            @Param("id") Long id, @Param("executionId") Long executionId,
            @Param("tripId") Long tripId, @Param("waypointId") Long waypointId,
            @Param("userId") Long userId, @Param("firstInsideAt") LocalDateTime firstInsideAt,
            @Param("confirmedAt") LocalDateTime confirmedAt,
            @Param("evidenceCount") int evidenceCount, @Param("distance") int distance);
}
