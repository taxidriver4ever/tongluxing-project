package com.tongdao.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.trip.entity.Trip;

@Mapper
public interface TripMapper {

    @Select("""
            select id, user_id, vehicle_id, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   created_at, updated_at, deleted
            from trip
            where id = #{tripId} and deleted = 0
            limit 1
            """)
    Trip findById(@Param("tripId") Long tripId);

    @Select("""
            select id, user_id, vehicle_id, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and deleted = 0
              and status in ('PUBLISHED', 'ONGOING')
            order by departure_time asc
            """)
    List<Trip> findActiveByUserId(@Param("userId") Long userId);

    @Select("""
            select id, user_id, vehicle_id, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and deleted = 0
              and status in ('ENDED', 'CANCELLED', 'ARCHIVED')
            order by departure_time desc
            limit #{limit}
            """)
    List<Trip> findHistoryByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    @Select("""
            select id, user_id, vehicle_id, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   created_at, updated_at, deleted
            from trip
            where public_flag = 1 and deleted = 0
              and status in ('PUBLISHED', 'ONGOING')
            order by departure_time asc
            limit #{limit}
            """)
    List<Trip> findPublicTrips(@Param("limit") Integer limit);

    @Insert("""
            insert into trip
                (id, user_id, vehicle_id, start_name, start_lat, start_lng,
                 start_location_name, start_location_address, start_latitude, start_longitude,
                 end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                 route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                 departure_time, estimated_days, total_distance_meters,
                 max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{vehicleId}, #{startName}, #{startLat}, #{startLng},
                 #{startLocationName}, #{startLocationAddress}, #{startLatitude}, #{startLongitude},
                 #{endName}, #{endLat}, #{endLng}, #{endLocationName}, #{endLocationAddress}, #{endLatitude}, #{endLongitude},
                 #{routeSummary}, #{routePolylineKey}, #{routeDistance}, #{routeDuration}, #{routePolyline}, #{waypointsJson},
                 #{departureTime}, #{estimatedDays}, #{totalDistanceMeters},
                 #{maxVehicleCount}, #{joinedVehicleCount}, #{travelDepth}, #{publicFlag}, #{status}, #{remark},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(Trip trip);

    @Update("""
            update trip
            set vehicle_id = #{vehicleId},
                start_name = #{startName},
                start_lat = #{startLat},
                start_lng = #{startLng},
                start_location_name = #{startLocationName},
                start_location_address = #{startLocationAddress},
                start_latitude = #{startLatitude},
                start_longitude = #{startLongitude},
                end_name = #{endName},
                end_lat = #{endLat},
                end_lng = #{endLng},
                end_location_name = #{endLocationName},
                end_location_address = #{endLocationAddress},
                end_latitude = #{endLatitude},
                end_longitude = #{endLongitude},
                route_summary = #{routeSummary},
                route_polyline_key = #{routePolylineKey},
                route_distance = #{routeDistance},
                route_duration = #{routeDuration},
                route_polyline = #{routePolyline},
                waypoints_json = #{waypointsJson},
                departure_time = #{departureTime},
                estimated_days = #{estimatedDays},
                total_distance_meters = #{totalDistanceMeters},
                max_vehicle_count = #{maxVehicleCount},
                travel_depth = #{travelDepth},
                public_flag = #{publicFlag},
                remark = #{remark},
                updated_at = #{updatedAt}
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    void update(Trip trip);

    @Update("""
            update trip
            set status = #{status}, updated_at = #{updatedAt}
            where id = #{tripId} and user_id = #{userId} and deleted = 0
            """)
    int updateStatus(@Param("tripId") Long tripId, @Param("userId") Long userId, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);
}
