package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.drivertrack.entity.DriverTrackDistanceRecord;

/**
 * 驾驶轨迹里程结算 Mapper。
 */
@Mapper
public interface DriverTrackDistanceRecordMapper {

    @Select("""
            select id, trip_id, driver_id, total_distance, last_settle_distance, settle_type,
                   settle_key, settle_time, event_published, created_at, updated_at, deleted
            from driver_track_distance_record
            where trip_id = #{tripId} and driver_id = #{driverId} and deleted = 0
            order by updated_at desc
            limit 1
            """)
    DriverTrackDistanceRecord findLatest(@Param("tripId") Long tripId, @Param("driverId") Long driverId);

    @Select("""
            select id, trip_id, driver_id, total_distance, last_settle_distance, settle_type,
                   settle_key, settle_time, event_published, created_at, updated_at, deleted
            from driver_track_distance_record
            where settle_key = #{settleKey} and deleted = 0
            limit 1
            """)
    DriverTrackDistanceRecord findBySettleKey(@Param("settleKey") String settleKey);

    /** 历史兼容统计；修订版结算禁止跨行程保留不足 5 公里的余量。 */
    @Select("""
            select coalesce(sum(distance_from_prev), 0)
            from driver_track_record
            where driver_id = #{driverId} and deleted = 0
            """)
    int sumTrackedDistance(@Param("driverId") Long driverId);

    /** 历史阶段记录数量；仅用于兼容旧数据，不参与修订版成长值结算。 */
    @Select("""
            select count(*)
            from driver_track_distance_record
            where driver_id = #{driverId} and settle_type = 'MILEAGE_STAGE' and deleted = 0
            """)
    int countMileageStages(@Param("driverId") Long driverId);

    @Insert("""
            insert into driver_track_distance_record
                (id, trip_id, driver_id, total_distance, last_settle_distance, settle_type,
                 settle_key, settle_time, event_published, created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{driverId}, #{totalDistance}, #{lastSettleDistance}, #{settleType},
                 #{settleKey}, #{settleTime}, #{eventPublished}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(DriverTrackDistanceRecord record);

    @Update("""
            update driver_track_distance_record
            set total_distance = #{totalDistance},
                updated_at = #{updatedAt}
            where id = #{id} and deleted = 0
            """)
    int updateTotalDistance(@Param("id") Long id, @Param("totalDistance") Integer totalDistance, @Param("updatedAt") LocalDateTime updatedAt);
}
