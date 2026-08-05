package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 自动出发前的成员范围异常 Mapper。 */
@Mapper
public interface TripDepartureExceptionMapper {

    @Insert("""
            insert into trip_departure_exception
                (id, trip_id, member_user_id, distance_m, exception_type, exception_status,
                 detected_at, created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{memberUserId}, #{distanceM}, #{exceptionType}, 'PENDING',
                 #{now}, #{now}, #{now}, 0)
            on duplicate key update distance_m = values(distance_m), exception_type = values(exception_type),
                detected_at = values(detected_at), updated_at = values(updated_at)
            """)
    int upsertPending(@Param("id") Long id, @Param("tripId") Long tripId,
                      @Param("memberUserId") Long memberUserId, @Param("distanceM") Integer distanceM,
                      @Param("exceptionType") String exceptionType, @Param("now") LocalDateTime now);

    @Select("""
            select id, trip_id as tripId, member_user_id as memberUserId, distance_m as distanceM,
                   exception_type as exceptionType, exception_status as exceptionStatus,
                   handled_action as handledAction, detected_at as detectedAt, handled_at as handledAt
            from trip_departure_exception
            where trip_id = #{tripId} and exception_status in ('PENDING', 'WAITING') and deleted = 0
            order by detected_at asc
            """)
    List<Map<String, Object>> findPending(@Param("tripId") Long tripId);

    @Update("""
            update trip_departure_exception
            set exception_status = case when #{action} = 'WAIT' then 'WAITING' else 'IGNORED' end,
                handled_action = #{action}, handled_at = #{now}, updated_at = #{now}
            where trip_id = #{tripId} and exception_status in ('PENDING', 'WAITING') and deleted = 0
            """)
    int handleAll(@Param("tripId") Long tripId, @Param("action") String action,
                  @Param("now") LocalDateTime now);

    @Update("""
            update trip_departure_exception
            set exception_status = 'RESOLVED', updated_at = #{now}
            where trip_id = #{tripId} and exception_status in ('PENDING', 'WAITING') and deleted = 0
            """)
    int resolveAll(@Param("tripId") Long tripId, @Param("now") LocalDateTime now);
}
