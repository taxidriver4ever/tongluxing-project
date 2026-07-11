package com.tongluxing.drivertrack.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.drivertrack.entity.DriverDeviationRecord;

/**
 * 偏航记录 Mapper。
 */
@Mapper
public interface DriverDeviationRecordMapper {

    @Insert("""
            insert into driver_track_deviation_record
                (id, trip_id, driver_id, longitude, latitude, deviation_distance,
                 deviation_status, record_time, created_at, deleted)
            values
                (#{id}, #{tripId}, #{driverId}, #{longitude}, #{latitude}, #{deviationDistance},
                 #{deviationStatus}, #{recordTime}, #{createdAt}, 0)
            """)
    int insert(DriverDeviationRecord record);

    @Select("""
            select id, trip_id, driver_id, longitude, latitude, deviation_distance,
                   deviation_status, record_time, created_at, deleted
            from driver_track_deviation_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
            order by record_time desc
            limit 1
            """)
    DriverDeviationRecord findLatest(@Param("tripId") Long tripId, @Param("driverId") Long driverId);
}
