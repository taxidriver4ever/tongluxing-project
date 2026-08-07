package com.tongluxing.trip.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.query.TripMatchCandidateRow;

/**
 * 行程主表 Mapper。
 */
@Mapper
public interface TripMapper {

    /**
     * 根据行程 ID 查询未删除行程。
     */
    @Select("""
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where id = #{tripId} and deleted = 0
            limit 1
            """)
    Trip findById(@Param("tripId") Long tripId);

    /** 根据公开行程号精确查询，不支持前缀或模糊匹配。 */
    @Select("""
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth,
                   public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark, actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where trip_number = #{tripNumber} and deleted = 0
            limit 1
            """)
    Trip findByTripNumber(@Param("tripNumber") String tripNumber);

    /**
     * 查询用户当前活跃行程。
     */
    @Select("""
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and deleted = 0
              and status in ('PUBLISHED', 'READY', 'CONFIRMING', 'RUNNING', 'ONGOING')
            order by departure_time asc
            """)
    List<Trip> findActiveByUserId(@Param("userId") Long userId);

    /**
     * 查询推荐模块使用的自有基准行程。
     *
     * <p>进行中优先，其次按计划出发时间选择最近一条待出发行程。只读取用户自己
     * 发布的行程，保证距离和时间基准符合推荐规则。</p>
     */
    @Select("""
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
                   actual_start_time, actual_end_time, created_at, updated_at, deleted
            from trip
            where user_id = #{userId}
              and deleted = 0
              and departure_time is not null
              and status in ('PUBLISHED', 'RECRUITING', 'READY', 'CONFIRMING', 'RUNNING', 'ONGOING')
              and (status in ('RUNNING', 'ONGOING') or departure_time >= now())
            order by case when status in ('RUNNING', 'ONGOING') then 0 else 1 end,
                     case when departure_time >= now() then 0 else 1 end,
                     abs(timestampdiff(second, now(), departure_time)),
                     created_at desc
            limit 1
            """)
    Trip findRecommendationReferenceByUserId(@Param("userId") Long userId);

    /** 查询已经到达计划出发时间、允许自动出发且仍有队长的行程。 */
    @Select("""
            select *
            from trip
            where deleted = 0
              and captain_user_id is not null
              and auto_start_enabled = 1
              and departure_time <= #{now}
              and status in ('PUBLISHED', 'READY', 'CONFIRMING')
            order by departure_time asc, created_at asc
            limit #{limit}
            """)
    List<Trip> findDueAutoStartTrips(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 查询需要进行到达判断和成员定位检查的行驶中行程。 */
    @Select("""
            select *
            from trip
            where deleted = 0
              and captain_user_id is not null
              and status in ('RUNNING', 'ONGOING')
            order by coalesce(actual_start_time, updated_at) asc
            limit #{limit}
            """)
    List<Trip> findRunningTrips(@Param("limit") int limit);

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
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
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
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
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
     * 查询当前用户主动退出过的行程。成员快照保留退出事实，即使行程仍在进行或已经结束，
     * 都会出现在“历史与结算-已退出”中。
     */
    @Select("""
            select distinct
                   t.id, t.trip_number, t.user_id, t.trip_type, t.publisher_role, t.captain_user_id,
                   t.auto_start_enabled, t.vehicle_id, t.title, t.description, t.expected_people,
                   t.start_name, t.start_lat, t.start_lng,
                   t.start_location_name, t.start_location_address, t.start_latitude, t.start_longitude,
                   t.end_name, t.end_lat, t.end_lng,
                   t.end_location_name, t.end_location_address, t.end_latitude, t.end_longitude,
                   t.route_summary, t.route_polyline_key, t.route_distance, t.route_duration,
                   t.waypoints_json, t.departure_time, t.estimated_days,
                   t.total_distance_meters, t.max_vehicle_count, t.joined_vehicle_count,
                   t.vehicle_requirements, t.budget_description, t.travel_depth, t.public_flag,
                   t.status, t.arrival_status, t.arrival_entered_at, t.arrival_decision_deadline,
                   t.continue_count, t.remark, t.actual_start_time, t.actual_end_time,
                   t.created_at, t.updated_at, t.deleted
            from trip t
            where t.deleted = 0
              and (
                exists (
                  select 1
                  from trip_member_snapshot member
                  where member.trip_id = t.id
                    and member.user_id = #{userId}
                    and member.join_status in ('EXITED', 'EXITED_DURING_TRIP', 'EXITED_AFTER_TRIP')
                )
                or exists (
                  select 1
                  from chat_conversation conversation
                  join chat_conversation_member chat_member
                    on chat_member.conversation_id = conversation.id
                   and chat_member.user_id = #{userId}
                   and chat_member.member_status = 'EXITED'
                   and chat_member.deleted = 0
                  where conversation.biz_type = 'TRIP'
                    and conversation.biz_id = t.id
                    and conversation.conversation_status = 'ARCHIVED'
                    and conversation.deleted = 0
                )
              )
            order by t.departure_time desc
            limit #{limit}
            """)
    List<Trip> findExitedByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 查询公开且仍可参与的行程列表。
     */
    @Select("""
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where public_flag = 1 and deleted = 0
              and status in ('PUBLISHED', 'RUNNING', 'ONGOING')
            order by case when #{userId} is not null and user_id = #{userId} then 0 else 1 end,
                     departure_time asc
            limit #{limit}
            """)
    List<Trip> findPublicTrips(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 推荐/发现专用轻量候选查询。
     *
     * <p>完整 polyline 不在此 SQL 中读取；用户资料、成长摘要、徽章数和车辆展示信息
     * 一次聚合完成，避免 MatchTripAdapter 在候选池上产生 N+1。</p>
     */
    @Select("""
            <script>
            select
                t.id tripId, t.trip_number tripNumber, t.user_id userId, t.vehicle_id vehicleId,
                t.trip_type tripType, t.publisher_role publisherRole, t.captain_user_id captainUserId,
                coalesce(nullif(p.nickname,''), '同路行车友') ownerNickname,
                p.avatar_image_key ownerAvatarImageKey,
                case when exists (
                    select 1 from user_driving_license_certification c
                    where c.user_id=t.user_id and c.deleted=0 and c.certification_status='APPROVED'
                ) then true else false end driverVerified,
                coalesce(g.level_code, 'LV1') ownerLevelCode,
                coalesce(us.total_trip_count,0) ownerTotalTripCount,
                coalesce(us.total_distance_meters,0) ownerTotalDistanceMeters,
                a.last_login_time ownerLastActiveAt,
                coalesce(b.badge_count,0) ownerBadgeCount,
                v.vehicle_type vehicleType, v.brand vehicleBrand, v.model vehicleModel,
                t.vehicle_requirements vehicleRequirements, t.budget_description budgetDescription,
                t.title, t.description, t.start_name startName, t.end_name endName,
                coalesce(t.start_latitude,t.start_lat) startLatitude,
                coalesce(t.start_longitude,t.start_lng) startLongitude,
                coalesce(t.end_latitude,t.end_lat) endLatitude,
                coalesce(t.end_longitude,t.end_lng) endLongitude,
                t.departure_time departureTime, t.estimated_days estimatedDays,
                t.route_distance routeDistance, t.route_duration routeDuration,
                t.waypoints_json waypointsJson, t.remark, t.travel_depth travelDepth,
                t.expected_people expectedPeople, t.max_vehicle_count maxVehicleCount,
                t.joined_vehicle_count joinedVehicleCount, t.status, t.public_flag publicFlag
            from trip t
            left join user_profile p on p.user_id=t.user_id and p.deleted=0
            left join user_statistics us on us.user_id=t.user_id
            left join growth_account g on g.user_id=t.user_id and g.deleted=0
            left join auth_account a on a.user_id=t.user_id and a.deleted=0
            left join vehicle_profile v on v.id=t.vehicle_id and v.deleted=0
            left join (
                select user_id, count(*) badge_count
                from growth_user_badge
                where deleted=0
                group by user_id
            ) b on b.user_id=t.user_id
            where t.public_flag=1 and t.deleted=0
              and t.status in ('PUBLISHED','RECRUITING','RUNNING','ONGOING')
            <if test='excludeUserId != null'>and t.user_id &lt;&gt; #{excludeUserId}</if>
            <if test='ownerUserId != null'>and t.user_id = #{ownerUserId}</if>
            <if test='departureFrom != null'>and t.departure_time &gt;= #{departureFrom}</if>
            <if test='departureTo != null'>and t.departure_time &lt;= #{departureTo}</if>
            <if test='startCity != null and startCity != ""'>
              and lower(t.start_name) like concat('%', lower(#{startCity}), '%')
            </if>
            <if test='destination != null and destination != ""'>
              and lower(t.end_name) like concat('%', lower(#{destination}), '%')
            </if>
            order by t.departure_time asc, t.id asc
            limit #{limit}
            </script>
            """)
    List<TripMatchCandidateRow> findMatchCandidates(
            @Param("excludeUserId") Long excludeUserId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("departureFrom") LocalDateTime departureFrom,
            @Param("departureTo") LocalDateTime departureTo,
            @Param("startCity") String startCity,
            @Param("destination") String destination,
            @Param("limit") Integer limit);

    /**
     * 新增行程主表记录。
     */
    @Insert("""
            insert into trip
                (id, trip_number, user_id, trip_type, publisher_role, captain_user_id, vehicle_id,
                 title, description, expected_people, start_name, start_lat, start_lng,
                 start_location_name, start_location_address, start_latitude, start_longitude,
                 end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                 route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                 departure_time, estimated_days, total_distance_meters,
                 max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth,
                 public_flag, status, auto_start_enabled, arrival_status, arrival_entered_at,
                 arrival_decision_deadline, continue_count, remark,
                 actual_start_time, actual_end_time,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{tripNumber}, #{userId}, #{tripType}, #{publisherRole}, #{captainUserId}, #{vehicleId},
                 #{title}, #{description}, #{expectedPeople}, #{startName}, #{startLat}, #{startLng},
                 #{startLocationName}, #{startLocationAddress}, #{startLatitude}, #{startLongitude},
                 #{endName}, #{endLat}, #{endLng}, #{endLocationName}, #{endLocationAddress}, #{endLatitude}, #{endLongitude},
                 #{routeSummary}, #{routePolylineKey}, #{routeDistance}, #{routeDuration}, #{waypointsJson},
                 #{departureTime}, #{estimatedDays}, #{totalDistanceMeters},
                 #{maxVehicleCount}, #{joinedVehicleCount}, #{vehicleRequirements}, #{budgetDescription}, #{travelDepth},
                 #{publicFlag}, #{status}, #{autoStartEnabled}, #{arrivalStatus}, #{arrivalEnteredAt},
                 #{arrivalDecisionDeadline}, #{continueCount}, #{remark},
                 #{actualStartTime}, #{actualEndTime},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(Trip trip);

    /**
     * 更新行程路线、时间、人数和公开信息。
     */
    @Update("""
            update trip
            set trip_type = #{tripType},
                publisher_role = #{publisherRole},
                captain_user_id = #{captainUserId},
                auto_start_enabled = #{autoStartEnabled},
                vehicle_id = #{vehicleId},
                title = #{title},
                description = #{description},
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
                waypoints_json = #{waypointsJson},
                departure_time = #{departureTime},
                estimated_days = #{estimatedDays},
                total_distance_meters = #{totalDistanceMeters},
                max_vehicle_count = #{maxVehicleCount},
                vehicle_requirements = #{vehicleRequirements},
                budget_description = #{budgetDescription},
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
                arrival_status = 'NOT_ARRIVED',
                arrival_entered_at = null,
                arrival_decision_deadline = null,
                updated_at = #{actualStartTime}
            where id = #{tripId}
              and user_id = #{userId}
              and status in ('PUBLISHED', 'READY', 'CONFIRMING')
              and deleted = 0
            """)
    int startTrip(@Param("tripId") Long tripId, @Param("userId") Long userId, @Param("actualStartTime") LocalDateTime actualStartTime);

    /** 修复旧数据：行程创建者必须同时是队长。 */
    @Update("""
            update trip
            set captain_user_id = user_id
            where id = #{tripId}
              and user_id = #{userId}
              and (captain_user_id is null or captain_user_id <> user_id)
              and deleted = 0
            """)
    int ensureCreatorCaptain(@Param("tripId") Long tripId, @Param("userId") Long userId);

    /** 定时任务自动开始行程；状态条件保证重复扫描幂等。 */
    @Update("""
            update trip
            set status = 'RUNNING',
                actual_start_time = coalesce(actual_start_time, #{startedAt}),
                arrival_status = 'NOT_ARRIVED',
                arrival_entered_at = null,
                arrival_decision_deadline = null,
                updated_at = #{startedAt}
            where id = #{tripId}
              and captain_user_id is not null
              and auto_start_enabled = 1
              and status in ('PUBLISHED', 'READY', 'CONFIRMING')
              and deleted = 0
            """)
    int autoStartTrip(@Param("tripId") Long tripId, @Param("startedAt") LocalDateTime startedAt);

    /** 首次进入终点范围时开始计算停留时间。 */
    @Update("""
            update trip
            set arrival_status = 'DWELLING',
                arrival_entered_at = #{enteredAt},
                arrival_decision_deadline = null,
                updated_at = #{enteredAt}
            where id = #{tripId}
              and status in ('RUNNING', 'ONGOING')
              and arrival_status in ('NOT_ARRIVED', 'CONTINUING')
              and deleted = 0
            """)
    int markArrivalDwelling(@Param("tripId") Long tripId,
                            @Param("enteredAt") LocalDateTime enteredAt);

    /** 连续停留达到阈值后进入“结束或继续”待确认状态。 */
    @Update("""
            update trip
            set arrival_status = 'AWAITING_DECISION',
                arrival_entered_at = #{enteredAt},
                arrival_decision_deadline = #{deadline},
                updated_at = current_timestamp
            where id = #{tripId}
              and status in ('RUNNING', 'ONGOING')
              and arrival_status = 'DWELLING'
              and deleted = 0
            """)
    int markArrivalPending(@Param("tripId") Long tripId,
                           @Param("enteredAt") LocalDateTime enteredAt,
                           @Param("deadline") LocalDateTime deadline);

    /** 停留期间离开终点范围时重置本轮到达判断。 */
    @Update("""
            update trip
            set arrival_status = 'NOT_ARRIVED',
                arrival_entered_at = null,
                arrival_decision_deadline = null,
                updated_at = #{updatedAt}
            where id = #{tripId}
              and status in ('RUNNING', 'ONGOING')
              and arrival_status = 'DWELLING'
              and deleted = 0
            """)
    int resetArrivalDwelling(@Param("tripId") Long tripId,
                             @Param("updatedAt") LocalDateTime updatedAt);

    /** 到达终点后结束行程；只允许待确认状态推进到结束。 */
    @Update("""
            update trip
            set status = 'FINISHED',
                arrival_status = 'ENDED',
                actual_end_time = coalesce(actual_end_time, #{endedAt}),
                updated_at = #{endedAt}
            where id = #{tripId}
              and status in ('RUNNING', 'ONGOING')
              and arrival_status = 'AWAITING_DECISION'
              and deleted = 0
            """)
    int finishArrivedTrip(@Param("tripId") Long tripId, @Param("endedAt") LocalDateTime endedAt);

    /** 到达后继续前往新终点，并清理上一轮到达状态。 */
    @Update("""
            update trip
            set end_name = #{endName},
                end_lat = #{endLatitude},
                end_lng = #{endLongitude},
                end_location_name = #{endName},
                end_location_address = #{endAddress},
                end_latitude = #{endLatitude},
                end_longitude = #{endLongitude},
                arrival_status = 'CONTINUING',
                arrival_entered_at = null,
                arrival_decision_deadline = null,
                continue_count = coalesce(continue_count, 0) + 1,
                updated_at = #{updatedAt}
            where id = #{tripId}
              and captain_user_id = #{userId}
              and status in ('RUNNING', 'ONGOING')
              and arrival_status = 'AWAITING_DECISION'
              and deleted = 0
            """)
    int continueTrip(@Param("tripId") Long tripId,
                     @Param("userId") Long userId,
                     @Param("endName") String endName,
                     @Param("endAddress") String endAddress,
                     @Param("endLatitude") java.math.BigDecimal endLatitude,
                     @Param("endLongitude") java.math.BigDecimal endLongitude,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 结束行程，只允许 RUNNING -> FINISHED；兼容迁移前的 ONGOING。
     */
    @Update("""
            update trip
            set status = 'FINISHED',
                arrival_status = 'ENDED',
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
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
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
            select id, trip_number, user_id, trip_type, publisher_role, captain_user_id, auto_start_enabled, vehicle_id, title, description, expected_people, start_name, start_lat, start_lng,
                   start_location_name, start_location_address, start_latitude, start_longitude,
                   end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
                   route_summary, route_polyline_key, route_distance, route_duration, waypoints_json,
                   departure_time, estimated_days, total_distance_meters,
                   max_vehicle_count, joined_vehicle_count, vehicle_requirements, budget_description, travel_depth, public_flag, status, arrival_status, arrival_entered_at, arrival_decision_deadline, continue_count, remark,
                   actual_start_time, actual_end_time,
                   created_at, updated_at, deleted
            from trip
            where user_id = #{userId} and status in ('RUNNING', 'ONGOING') and deleted = 0
            order by actual_start_time desc, updated_at desc
            limit 1
            """)
    Trip findCurrentDrivingByUserId(@Param("userId") Long userId);
}
