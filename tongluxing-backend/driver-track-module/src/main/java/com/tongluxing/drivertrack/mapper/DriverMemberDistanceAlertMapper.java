package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DriverMemberDistanceAlertMapper {
    @Select("""
            select id, alert_level as alertLevel, started_at as startedAt
            from trip_member_distance_alert
            where trip_id=#{tripId} and member_user_id=#{memberId}
              and recovered_at is null and deleted=0
            order by created_at desc limit 1
            """)
    Map<String, Object> findActive(@Param("tripId") Long tripId, @Param("memberId") Long memberId);

    @Insert("""
            insert into trip_member_distance_alert
              (id,trip_id,captain_user_id,member_user_id,alert_level,distance_m,
               started_at,created_at,updated_at,deleted)
            values
              (#{id},#{tripId},#{captainId},#{memberId},#{level},#{distance},
               #{now},#{now},#{now},0)
            """)
    int insert(@Param("id") Long id, @Param("tripId") Long tripId,
               @Param("captainId") Long captainId, @Param("memberId") Long memberId,
               @Param("level") String level, @Param("distance") int distance,
               @Param("now") LocalDateTime now);

    @Update("""
            update trip_member_distance_alert
            set alert_level=#{level}, distance_m=#{distance}, updated_at=#{now},
                notified_at=case when #{notify}=1 then coalesce(notified_at,#{now}) else notified_at end
            where id=#{id} and deleted=0
            """)
    int update(@Param("id") Long id, @Param("level") String level,
               @Param("distance") int distance, @Param("notify") int notify,
               @Param("now") LocalDateTime now);

    @Update("""
            update trip_member_distance_alert
            set recovered_at=#{now}, updated_at=#{now}
            where id=#{id} and recovered_at is null and deleted=0
            """)
    int recover(@Param("id") Long id, @Param("now") LocalDateTime now);
}
