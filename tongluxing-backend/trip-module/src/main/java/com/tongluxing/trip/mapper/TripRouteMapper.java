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
            select id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline,
                   plan_distance, plan_duration, provider_type, route_status, created_at, updated_at, deleted
            from trip_route
            where trip_id = #{tripId} and deleted = 0
            limit 1
            """)
    TripRoute findByTripId(@Param("tripId") Long tripId);

    @Select("""
            select id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline,
                   plan_distance, plan_duration, provider_type, route_status, created_at, updated_at, deleted
            from trip_route where draft_id = #{draftId} and deleted = 0 limit 1
            """)
    TripRoute findByDraftId(@Param("draftId") Long draftId);

    @Insert("""
            insert into trip_route
                (id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline,
                 plan_distance, plan_duration, provider_type, route_status, created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{draftId}, #{routePlanId}, #{origin}, #{destination}, #{waypoints}, #{polyline},
                 #{planDistance}, #{planDuration}, #{providerType}, #{routeStatus}, #{createdAt}, #{updatedAt}, 0)
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
                route_plan_id = #{routePlanId},
                provider_type = #{providerType},
                route_status = #{routeStatus},
                updated_at = #{updatedAt}
            where trip_id = #{tripId} and deleted = 0
            """)
    int updateByTripId(TripRoute route);

    @Update("""
            update trip_route set route_plan_id=#{routePlanId}, origin=#{origin}, destination=#{destination},
                waypoints=#{waypoints}, polyline=#{polyline}, plan_distance=#{planDistance}, plan_duration=#{planDuration},
                provider_type=#{providerType}, route_status=#{routeStatus}, updated_at=#{updatedAt}
            where draft_id=#{draftId} and deleted=0
            """)
    int updateByDraftId(TripRoute route);

    @Update("update trip_route set route_status='STALE', updated_at=#{now} where draft_id=#{draftId} and deleted=0")
    int markDraftRouteStale(@Param("draftId") Long draftId, @Param("now") java.time.LocalDateTime now);
}
