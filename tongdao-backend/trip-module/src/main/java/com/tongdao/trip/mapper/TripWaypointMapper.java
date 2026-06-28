package com.tongdao.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.trip.entity.TripWaypoint;

/**
 * 行程途经点 Mapper。
 */
@Mapper
public interface TripWaypointMapper {

    /**
     * 查询行程途经点列表。
     */
    @Select("""
            select id, trip_id, seq_no, place_name, lat, lng, stay_minutes, created_at, updated_at, deleted
            from trip_waypoint
            where trip_id = #{tripId} and deleted = 0
            order by seq_no asc
            """)
    List<TripWaypoint> findByTripId(@Param("tripId") Long tripId);

    /**
     * 新增行程途经点。
     */
    @Insert("""
            insert into trip_waypoint
                (id, trip_id, seq_no, place_name, lat, lng, stay_minutes, created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{seqNo}, #{placeName}, #{lat}, #{lng}, #{stayMinutes}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(TripWaypoint waypoint);

    /**
     * 删除指定行程下的全部途经点。
     */
    @Delete("""
            delete from trip_waypoint
            where trip_id = #{tripId}
            """)
    void deleteByTripId(@Param("tripId") Long tripId);
}
