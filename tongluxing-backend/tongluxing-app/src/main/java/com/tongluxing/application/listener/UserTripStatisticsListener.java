package com.tongluxing.application.listener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.tongluxing.trip.service.TripFinishedEvent;
import lombok.RequiredArgsConstructor;

/** 行程结束后由服务端累计用户出行统计，客户端不能直接修改。 */
@Component
@RequiredArgsConstructor
public class UserTripStatisticsListener {
    private final JdbcTemplate jdbc;

    @Async("tripEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripFinished(TripFinishedEvent event) {
        List<java.util.Map<String, Object>> trips = jdbc.queryForList("""
            select user_id,coalesce(total_distance_meters,route_distance,0) distance_meters,
              greatest(0,timestampdiff(minute,actual_start_time,actual_end_time)) duration_minutes,
              (select count(*) from trip_waypoint w where w.trip_id=t.id and w.deleted=0) waypoint_count
            from trip t where id=? and deleted=0
            """, event.tripId());
        if (trips.isEmpty()) return;
        var trip = trips.get(0);
        Set<Long> users = new LinkedHashSet<>();
        users.add(((Number) trip.get("user_id")).longValue());
        users.addAll(jdbc.query("select user_id from trip_member_snapshot where trip_id=? and join_status in ('APPROVED','JOINED')",
                (rs, n) -> rs.getLong(1), event.tripId()));
        long distance = ((Number) trip.get("distance_meters")).longValue();
        long duration = ((Number) trip.get("duration_minutes")).longValue();
        int waypoints = ((Number) trip.get("waypoint_count")).intValue();
        for (Long userId : users) {
            jdbc.update("""
                insert into user_statistics(user_id,total_trip_count,total_distance_meters,total_duration_minutes,completed_waypoint_count)
                values(?,1,?,?,?) on duplicate key update total_trip_count=total_trip_count+1,
                  total_distance_meters=total_distance_meters+values(total_distance_meters),
                  total_duration_minutes=total_duration_minutes+values(total_duration_minutes),
                  completed_waypoint_count=completed_waypoint_count+values(completed_waypoint_count)
                """, userId, distance, duration, waypoints);
        }
    }
}
