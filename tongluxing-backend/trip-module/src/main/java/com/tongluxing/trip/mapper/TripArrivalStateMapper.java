package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 到达停留与开放式结束状态表 Mapper。 */
@Mapper
public interface TripArrivalStateMapper {

    @Insert("""
            insert into trip_arrival_state
                (id, trip_id, captain_user_id, state, first_entered_at, prompted_at,
                 decision_deadline, last_distance_m, decision_action, decided_at,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{captainUserId}, #{state}, #{firstEnteredAt}, #{promptedAt},
                 #{decisionDeadline}, #{distanceM}, #{decisionAction}, #{decidedAt},
                 #{now}, #{now}, 0)
            on duplicate key update state = values(state), first_entered_at = values(first_entered_at),
                prompted_at = values(prompted_at), decision_deadline = values(decision_deadline),
                last_distance_m = values(last_distance_m), decision_action = values(decision_action),
                decided_at = values(decided_at), updated_at = values(updated_at)
            """)
    int upsert(@Param("id") Long id, @Param("tripId") Long tripId,
               @Param("captainUserId") Long captainUserId, @Param("state") String state,
               @Param("firstEnteredAt") LocalDateTime firstEnteredAt,
               @Param("promptedAt") LocalDateTime promptedAt,
               @Param("decisionDeadline") LocalDateTime decisionDeadline,
               @Param("distanceM") Integer distanceM,
               @Param("decisionAction") String decisionAction,
               @Param("decidedAt") LocalDateTime decidedAt,
               @Param("now") LocalDateTime now);

    @Update("""
            update trip_arrival_state
            set state = 'NOT_ARRIVED', first_entered_at = null, prompted_at = null,
                decision_deadline = null, last_distance_m = #{distanceM}, updated_at = #{now}
            where trip_id = #{tripId} and deleted = 0
            """)
    int reset(@Param("tripId") Long tripId, @Param("distanceM") Integer distanceM,
              @Param("now") LocalDateTime now);
}
