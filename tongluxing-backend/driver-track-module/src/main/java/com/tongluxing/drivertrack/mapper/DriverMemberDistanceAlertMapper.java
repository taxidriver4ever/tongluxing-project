package com.tongluxing.drivertrack.mapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 驾驶成员距离预警 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface DriverMemberDistanceAlertMapper {
    @Select("""
            select id, alert_level as alertLevel, started_at as startedAt, severe_started_at as severeStartedAt
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
            set severe_started_at=#{startedAt}, updated_at=#{now}
            where id=#{id} and recovered_at is null and deleted=0
            """)
    int setSevereStartedAt(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt,
                           @Param("now") LocalDateTime now);

    @Update("""
            update trip_member_distance_alert
            set severe_started_at=null, updated_at=#{now}
            where id=#{id} and recovered_at is null and deleted=0
            """)
    int clearSevereStartedAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            update trip_member_distance_alert
            set recovered_at=#{now}, updated_at=#{now}
            where id=#{id} and recovered_at is null and deleted=0
            """)
    int recover(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 查询队长尚未处理的脱队或失联异常。 */
    @Select("""
            select id, trip_id as tripId, captain_user_id as captainUserId,
                   member_user_id as memberUserId, alert_level as alertLevel,
                   distance_m as distanceM, started_at as startedAt, notified_at as notifiedAt,
                   handled_action as handledAction, handled_at as handledAt
            from trip_member_distance_alert
            where trip_id = #{tripId} and recovered_at is null and handled_at is null and deleted = 0
            order by case alert_level when 'MISSING' then 0 when 'SEVERE' then 1 else 2 end,
                     started_at asc
            """)
    List<Map<String, Object>> findPendingByTrip(@Param("tripId") Long tripId);

    /** 根据预警 ID 查询完整处理上下文，供队长处理接口做权限和成员校验。 */
    @Select("""
            select id, trip_id as tripId, captain_user_id as captainUserId,
                   member_user_id as memberUserId, alert_level as alertLevel,
                   distance_m as distanceM, started_at as startedAt, notified_at as notifiedAt,
                   handled_action as handledAction, handled_at as handledAt
            from trip_member_distance_alert
            where id = #{alertId} and deleted = 0
            limit 1
            """)
    Map<String, Object> findById(@Param("alertId") Long alertId);

    /** 队长忽略或移除成员后关闭异常处理闭环。 */
    @Update("""
            update trip_member_distance_alert
            set handled_action = #{action}, handled_by_user_id = #{operatorUserId},
                handled_at = #{now}, acknowledged_at = coalesce(acknowledged_at, #{now}), updated_at = #{now}
            where id = #{alertId} and captain_user_id = #{operatorUserId}
              and recovered_at is null and handled_at is null and deleted = 0
            """)
    int handle(@Param("alertId") Long alertId, @Param("operatorUserId") Long operatorUserId,
               @Param("action") String action, @Param("now") LocalDateTime now);

}
