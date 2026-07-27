package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.drivertrack.entity.DriverTrackRecord;

/** 驾驶轨迹点 Mapper。 */
@Mapper
public interface DriverTrackRecordMapper {

    String COLUMNS = """
            id, trip_id, driver_id, longitude, latitude, altitude, speed, direction, accuracy,
            raw_distance_from_prev, distance_from_prev, calculated_speed_kmh,
            provider, app_state, battery_level, device_id, sequence_no, mock_location,
            point_status, valid_point, risk_score, risk_flags, reject_reason,
            record_time, client_send_time, server_receive_time, created_at, deleted
            """;

    @Insert("""
            insert into driver_track_record
                (id, trip_id, driver_id, longitude, latitude, altitude, speed, direction, accuracy,
                 raw_distance_from_prev, distance_from_prev, calculated_speed_kmh,
                 provider, app_state, battery_level, device_id, sequence_no, mock_location,
                 point_status, valid_point, risk_score, risk_flags, reject_reason,
                 record_time, client_send_time, server_receive_time, created_at, deleted)
            values
                (#{id}, #{tripId}, #{driverId}, #{longitude}, #{latitude}, #{altitude}, #{speed}, #{direction}, #{accuracy},
                 #{rawDistanceFromPrev}, #{distanceFromPrev}, #{calculatedSpeedKmh},
                 #{provider}, #{appState}, #{batteryLevel}, #{deviceId}, #{sequenceNo}, #{mockLocation},
                 #{pointStatus}, #{validPoint}, #{riskScore}, #{riskFlags}, #{rejectReason},
                 #{recordTime}, #{clientSendTime}, #{serverReceiveTime}, #{createdAt}, 0)
            """)
    int insert(DriverTrackRecord record);

    @Select("""
            select
            """ + COLUMNS + """
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
            order by record_time desc
            limit 1
            """)
    DriverTrackRecord findLast(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select
            """ + COLUMNS + """
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and valid_point = 1
            order by record_time desc
            limit 1
            """)
    DriverTrackRecord findLastValid(@Param("tripId") Long tripId,
                                    @Param("driverId") Long driverId);

    @Select("""
            select
            """ + COLUMNS + """
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and record_time >= #{since}
            order by record_time asc
            """)
    List<DriverTrackRecord> findRecent(
            @Param("tripId") Long tripId,
            @Param("driverId") Long driverId,
            @Param("since") LocalDateTime since);

    @Select("""
            select
            """ + COLUMNS + """
            from driver_track_record
            where trip_id = #{tripId} and deleted = 0 and valid_point = 1
            order by record_time asc
            limit #{limit}
            """)
    List<DriverTrackRecord> findByTripId(@Param("tripId") Long tripId, @Param("limit") Integer limit);

    @Select("""
            select coalesce(sum(distance_from_prev), 0)
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and valid_point = 1
            """)
    Integer sumDistance(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select count(*)
            from driver_track_record
            where trip_id=#{tripId} and driver_id=#{driverId} and deleted=0
              and record_time >= #{since}
              and (point_status in ('IMPOSSIBLE_SPEED','TELEPORT','FATAL_REJECTED','ROUND_TRIP_RECOVERY')
                   or risk_flags like '%ABNORMAL_ACCELERATION%'
                   or risk_flags like '%ROUND_TRIP_TELEPORT%')
            """)
    int countRecentHardAnomalies(@Param("tripId") Long tripId,
                                 @Param("driverId") Long driverId,
                                 @Param("since") LocalDateTime since);
}
