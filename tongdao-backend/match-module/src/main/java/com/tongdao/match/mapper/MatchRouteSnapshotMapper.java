package com.tongdao.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import com.tongdao.match.entity.MatchRouteSnapshot;

@Mapper
public interface MatchRouteSnapshotMapper {

    @Insert("""
            insert into match_route_snapshot
                (id, trip_id, user_id, vehicle_id, start_name, start_address, start_latitude, start_longitude,
                 end_name, end_address, end_latitude, end_longitude, route_points_json, route_distance,
                 route_duration, departure_time, travel_depth, max_vehicle_count, public_flag, snapshot_status,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{userId}, #{vehicleId}, #{startName}, #{startAddress}, #{startLatitude}, #{startLongitude},
                 #{endName}, #{endAddress}, #{endLatitude}, #{endLongitude}, #{routePointsJson}, #{routeDistance},
                 #{routeDuration}, #{departureTime}, #{travelDepth}, #{maxVehicleCount}, #{publicFlag}, #{snapshotStatus},
                 #{createdAt}, #{updatedAt}, 0)
            on duplicate key update
                 user_id = values(user_id),
                 vehicle_id = values(vehicle_id),
                 start_name = values(start_name),
                 start_address = values(start_address),
                 start_latitude = values(start_latitude),
                 start_longitude = values(start_longitude),
                 end_name = values(end_name),
                 end_address = values(end_address),
                 end_latitude = values(end_latitude),
                 end_longitude = values(end_longitude),
                 route_points_json = values(route_points_json),
                 route_distance = values(route_distance),
                 route_duration = values(route_duration),
                 departure_time = values(departure_time),
                 travel_depth = values(travel_depth),
                 max_vehicle_count = values(max_vehicle_count),
                 public_flag = values(public_flag),
                 snapshot_status = values(snapshot_status),
                 updated_at = values(updated_at)
            """)
    void upsert(MatchRouteSnapshot snapshot);
}
