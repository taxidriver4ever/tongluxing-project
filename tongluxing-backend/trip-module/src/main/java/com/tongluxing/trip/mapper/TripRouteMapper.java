package com.tongluxing.trip.mapper;

import java.util.List;

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
            select id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline, match_polyline,
                   plan_distance, plan_duration, provider_type, route_status, route_signature,
                   created_at, updated_at, deleted
            from trip_route
            where trip_id = #{tripId} and deleted = 0
            limit 1
            """)
    TripRoute findByTripId(@Param("tripId") Long tripId);

    @Select("""
            select id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline, match_polyline,
                   plan_distance, plan_duration, provider_type, route_status, route_signature,
                   created_at, updated_at, deleted
            from trip_route where draft_id = #{draftId} and deleted = 0 limit 1
            """)
    TripRoute findByDraftId(@Param("draftId") Long draftId);

    /** 正式行程编辑未改路线时只读元数据，避免无意义加载 MEDIUMTEXT。 */
    @Select("""
            select id, trip_id, draft_id, route_plan_id, origin, destination, waypoints,
                   plan_distance, plan_duration, provider_type, route_status, route_signature,
                   created_at, updated_at, deleted
            from trip_route where trip_id = #{tripId} and deleted = 0 limit 1
            """)
    TripRoute findMetaByTripId(@Param("tripId") Long tripId);

    /** 发布校验只读路线元数据，不把 MEDIUMTEXT polyline 拉进 Java。 */
    @Select("""
            select id, trip_id, draft_id, route_plan_id, origin, destination, waypoints,
                   plan_distance, plan_duration, provider_type, route_status, route_signature,
                   created_at, updated_at, deleted
            from trip_route where draft_id = #{draftId} and deleted = 0 limit 1
            """)
    TripRoute findMetaByDraftId(@Param("draftId") Long draftId);

    /** 匹配精算只读取 RDP 简化路线，正常链路不触碰完整 MEDIUMTEXT polyline。 */
    @Select("""
            <script>
            select id, trip_id, match_polyline, updated_at
            from trip_route
            where deleted = 0 and trip_id in
            <foreach collection='tripIds' item='tripId' open='(' separator=',' close=')'>#{tripId}</foreach>
            </script>
            """)
    List<TripRoute> findMatchPolylinesByTripIds(@Param("tripIds") List<Long> tripIds);

    /** 仅用于历史数据懒回填：match_polyline 为空的行程才读取一次完整 polyline。 */
    @Select("""
            <script>
            select id, trip_id, polyline, updated_at
            from trip_route
            where deleted = 0
              and (match_polyline is null or char_length(match_polyline) = 0)
              and trip_id in
            <foreach collection='tripIds' item='tripId' open='(' separator=',' close=')'>#{tripId}</foreach>
            </script>
            """)
    List<TripRoute> findFullPolylinesForMatchBackfill(@Param("tripIds") List<Long> tripIds);

    @Update("""
            update trip_route
            set match_polyline=#{matchPolyline}
            where id=#{id} and deleted=0
            """)
    int updateMatchPolyline(TripRoute route);

    @Insert("""
            insert into trip_route
                (id, trip_id, draft_id, route_plan_id, origin, destination, waypoints, polyline, match_polyline,
                 plan_distance, plan_duration, provider_type, route_status, route_signature,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{draftId}, #{routePlanId}, #{origin}, #{destination}, #{waypoints}, #{polyline}, #{matchPolyline},
                 #{planDistance}, #{planDuration}, #{providerType}, #{routeStatus}, #{routeSignature},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(TripRoute route);

    @Update("""
            update trip_route
            set origin = #{origin},
                destination = #{destination},
                waypoints = #{waypoints},
                polyline = #{polyline},
                match_polyline = #{matchPolyline},
                plan_distance = #{planDistance},
                plan_duration = #{planDuration},
                route_plan_id = #{routePlanId},
                provider_type = #{providerType},
                route_status = #{routeStatus},
                route_signature = #{routeSignature},
                updated_at = #{updatedAt}
            where trip_id = #{tripId} and deleted = 0
            """)
    int updateByTripId(TripRoute route);

    @Update("""
            update trip_route set route_plan_id=#{routePlanId}, origin=#{origin}, destination=#{destination},
                waypoints=#{waypoints}, polyline=#{polyline}, match_polyline=#{matchPolyline}, plan_distance=#{planDistance}, plan_duration=#{planDuration},
                provider_type=#{providerType}, route_status=#{routeStatus}, route_signature=#{routeSignature}, updated_at=#{updatedAt}
            where draft_id=#{draftId} and deleted=0
            """)
    int updateByDraftId(TripRoute route);

    @Update("update trip_route set route_status='STALE', updated_at=#{now} where draft_id=#{draftId} and deleted=0")
    int markDraftRouteStale(@Param("draftId") Long draftId, @Param("now") java.time.LocalDateTime now);

    /** 发布草稿时直接把已验证路线提升为正式路线，不在 Java 层搬运巨大 polyline 字符串。 */
    @Update("""
            update trip_route
            set trip_id=#{tripId}, draft_id=null, updated_at=#{now}
            where draft_id=#{draftId} and deleted=0 and route_status='VALID'
            """)
    int promoteDraftRoute(@Param("draftId") Long draftId, @Param("tripId") Long tripId,
                          @Param("now") java.time.LocalDateTime now);
}
