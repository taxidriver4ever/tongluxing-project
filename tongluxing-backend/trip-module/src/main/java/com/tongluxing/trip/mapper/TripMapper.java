package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.Trip;

/**
 * 行程主表 Mapper。
 */
@Mapper
public interface TripMapper {

    /**
     * 根据行程 ID 查询未删除行程。
     */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where id = #{tripId} and deleted = 0
            limit 1
            """)
    Trip findById(@Param("tripId") Long tripId);

    /**
     * 查询用户当前活跃行程。
     */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and deleted = 0
              and status in ('PUBLISHED', 'READY', 'CONFIRMING', 'RUNNING', 'ONGOING')
            order by departure_time asc
            """)
    List<Trip> findActiveByUserId(@Param("userId") Long userId);

    /** 查询用户作为发布者拥有的进行中行程 ID。 */
    @Select("""
            select id
            from trip
            where user_id = #{userId}
              and deleted = 0
              and status in ('RUNNING', 'ONGOING')
            order by actual_start_time asc, created_at asc
            limit 1
            """)
    Long findRunningTripIdByUserId(@Param("userId") Long userId);

    /** 查询与新行程预计时间重叠的未来行程。 */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time, created_at, updated_at, deleted
            from trip
            where user_id = #{userId}
              and deleted = 0
              and status in ('PUBLISHED', 'READY', 'CONFIRMING')
              and (#{excludeTripId} is null or id <> #{excludeTripId})
              and departure_time < #{endTime}
              and timestampadd(day, greatest(coalesce(estimated_days, 1), 1), departure_time) > #{startTime}
            order by departure_time asc
            limit 1
            """)
    Trip findTimeConflict(@Param("userId") Long userId,
                          @Param("excludeTripId") Long excludeTripId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /** 查询除指定行程外，用户是否还有正在行驶的行程。 */
    @Select("""
            select id
            from trip
            where user_id = #{userId}
              and id <> #{tripId}
              and deleted = 0
              and status in ('RUNNING', 'ONGOING')
            order by actual_start_time asc, created_at asc
            limit 1
            """)
    Long findOtherRunningTripId(@Param("userId") Long userId, @Param("tripId") Long tripId);

    /**
     * 查询用户历史行程。
     */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and deleted = 0
              and status in ('FINISHED', 'SETTLED', 'ENDED', 'CANCELLED', 'ARCHIVED')
            order by departure_time desc
            limit #{limit}
            """)
    List<Trip> findHistoryByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 查询公开且仍可参与的行程列表。
     */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where public_flag = 1 and deleted = 0
              and status in ('PUBLISHED', 'RUNNING', 'ONGOING')
            order by departure_time asc
            limit #{limit}
            """)
    List<Trip> findPublicTrips(@Param("limit") Integer limit);

    /**
     * 新增行程主表记录。
     */
    @Insert("""
            insert into trip
                (id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                 start_location_name, start_location_address, start_latitude, start_longitude,
                 end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                 route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                 departure_time, estimated_days, total_distance_meters,
                 max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                 actual_start_time, actual_end_time,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{vehicleId}, #{title}, #{description}, #{coverImageKey}, #{expectedPeople}, #{startName}, #{startLat}, #{startLng},
                 #{startLocationName}, #{startLocationAddress}, #{startLatitude}, #{startLongitude},
                 #{endName}, #{endLat}, #{endLng}, #{endLocationName}, #{endLocationAddress}, #{endLatitude}, #{endLongitude},
                 #{routeSummary}, #{routePolylineKey}, #{routeDistance}, #{routeDuration}, #{routePolyline}, #{waypointsJson},
                 #{departureTime}, #{estimatedDays}, #{totalDistanceMeters},
                 #{maxVehicleCount}, #{joinedVehicleCount}, #{travelDepth}, #{publicFlag}, #{status}, #{remark},
                 #{actualStartTime}, #{actualEndTime},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(Trip trip);

    /**
     * 更新行程路线、时间、人数和公开信息。
     */
    @Update("""
            update trip
            set vehicle_id = #{vehicleId},
                title = #{title},
                description = #{description},
                cover_image_key = #{coverImageKey},
                expected_people = #{expectedPeople},
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

    /**
     * 更新行程状态，用于结束或取消行程。
     */
    @Update("""
            update trip
            set status = #{status}, updated_at = #{updatedAt}
            where id = #{tripId} and user_id = #{userId} and deleted = 0
            """)
    int updateStatus(@Param("tripId") Long tripId, @Param("userId") Long userId, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 开始行程，允许 PUBLISHED / READY / CONFIRMING -> RUNNING。
     */
    @Update("""
            update trip
            set status = 'RUNNING',
                actual_start_time = #{actualStartTime},
                updated_at = #{actualStartTime}
            where id = #{tripId}
              and user_id = #{userId}
              and status in ('PUBLISHED', 'READY', 'CONFIRMING')
              and deleted = 0
            """)
    int startTrip(@Param("tripId") Long tripId, @Param("userId") Long userId, @Param("actualStartTime") LocalDateTime actualStartTime);

    /**
     * 结束行程，只允许 RUNNING -> FINISHED；兼容迁移前的 ONGOING。
     */
    @Update("""
            update trip
            set status = 'FINISHED',
                actual_end_time = #{actualEndTime},
                updated_at = #{actualEndTime}
            where id = #{tripId}
              and user_id = #{userId}
              and status in ('RUNNING', 'ONGOING')
              and deleted = 0
            """)
    int endOngoingTrip(@Param("tripId") Long tripId, @Param("userId") Long userId, @Param("actualEndTime") LocalDateTime actualEndTime);

    /** 锁定当前用户的行程，供结束后的幂等结算使用。 */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time, created_at, updated_at, deleted
            from trip
            where id = #{tripId} and user_id = #{userId} and deleted = 0
            for update
            """)
    Trip findOwnedForUpdate(@Param("tripId") Long tripId, @Param("userId") Long userId);

    /** 完成结算，只允许 FINISHED -> SETTLED。 */
    @Update("""
            update trip
            set status = 'SETTLED', updated_at = #{settledAt}
            where id = #{tripId} and user_id = #{userId} and status = 'FINISHED' and deleted = 0
            """)
    int settleFinishedTrip(@Param("tripId") Long tripId, @Param("userId") Long userId,
                           @Param("settledAt") LocalDateTime settledAt);

    /**
     * 查询当前用户正在驾驶中的行程。
     */
    @Select("""
            select id, user_id, vehicle_id, title, description, cover_image_key, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, route_polyline, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and status in ('RUNNING', 'ONGOING') and deleted = 0
            order by actual_start_time desc, updated_at desc
            limit 1
            """)
    Trip findCurrentDrivingByUserId(@Param("userId") Long userId);
}
