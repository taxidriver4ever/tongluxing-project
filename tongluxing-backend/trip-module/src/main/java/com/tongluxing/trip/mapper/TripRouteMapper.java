package com.tongluxing.trip.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.TripRoute;

/**
 * 行程规划路线快照 Mapper。
 */
@Mapper
public interface TripRouteMapper {

    @Select("""
            select id, trip_id, origin, destination, waypoints, polyline,
                   plan_distance, plan_duration, created_at, updated_at, deleted
            from trip_route
            where trip_id = #{tripId} and deleted = 0
            limit 1
            """)
    TripRoute findByTripId(@Param("tripId") Long tripId);

    @Insert("""
            insert into trip_route
                (id, trip_id, origin, destination, waypoints, polyline,
                 plan_distance, plan_duration, created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{origin}, #{destination}, #{waypoints}, #{polyline},
                 #{planDistance}, #{planDuration}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(TripRoute route);

    @Update("""
            update trip_route
            set origin = #{origin},
                destination = #{destination},
                waypoints = #{waypoints},
                polyline = #{polyline},
                plan_distance = #{planDistance},
                plan_duration = #{planDuration},
                updated_at = #{updatedAt}
            where trip_id = #{tripId} and deleted = 0
            """)
    int updateByTripId(TripRoute route);
}
