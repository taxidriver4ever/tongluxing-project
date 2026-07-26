package com.tongluxing.drivertrack.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.drivertrack.entity.DriverTrackRecord;

/**
 * 驾驶轨迹点 Mapper。
 */
@Mapper
public interface DriverTrackRecordMapper {

    @Insert("""
            insert into driver_track_record
                (id, trip_id, driver_id, longitude, latitude, speed, direction, accuracy,
                 distance_from_prev, device_id, sequence_no, mock_location, point_status, valid_point,
                 record_time, created_at, deleted)
            values
                (#{id}, #{tripId}, #{driverId}, #{longitude}, #{latitude}, #{speed}, #{direction}, #{accuracy},
                 #{distanceFromPrev}, #{deviceId}, #{sequenceNo}, #{mockLocation}, #{pointStatus}, #{validPoint},
                 #{recordTime}, #{createdAt}, 0)
            """)
    int insert(DriverTrackRecord record);

    @Select("""
            select id, trip_id, driver_id, longitude, latitude, speed, direction, accuracy,
                   distance_from_prev, device_id, sequence_no, mock_location, point_status, valid_point,
                   record_time, created_at, deleted
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
            order by record_time desc
            limit 1
            """)
    DriverTrackRecord findLast(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select id, trip_id, driver_id, longitude, latitude, speed, direction, accuracy,
                   distance_from_prev, device_id, sequence_no, mock_location, point_status, valid_point,
                   record_time, created_at, deleted
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and valid_point = 1
            order by record_time desc
            limit 1
            """)
    DriverTrackRecord findLastValid(@Param("tripId") Long tripId,
                                    @Param("driverId") Long driverId);

    @Select("""
            select id, trip_id, driver_id, longitude, latitude, speed, direction, accuracy,
                   distance_from_prev, device_id, sequence_no, mock_location, point_status, valid_point,
                   record_time, created_at, deleted
            from driver_track_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
              and record_time >= #{since}
            order by record_time asc
            """)
    List<DriverTrackRecord> findRecent(
            @Param("tripId") Long tripId,
            @Param("driverId") Long driverId,
            @Param("since") java.time.LocalDateTime since);

    @Select("""
            select id, trip_id, driver_id, longitude, latitude, speed, direction, accuracy,
                   distance_from_prev, device_id, sequence_no, mock_location, point_status, valid_point,
                   record_time, created_at, deleted
            from driver_track_record
            where trip_id = #{tripId} and deleted = 0
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
}
