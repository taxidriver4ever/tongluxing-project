package com.tongluxing.team.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.team.entity.Team;

/**
 * 车队主表 Mapper。
 */
@Mapper
public interface TeamMapper {

    /**
     * 根据车队 ID 查询未删除车队。
     */
    @Select("""
            select id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                   start_name, end_name, departure_time, max_member_count, current_member_count,
                   join_mode, recruitment_status, allow_midway_join, deviation_warning_distance_m, deviation_warning_minutes,
                   severe_deviation_distance_m, severe_deviation_minutes, missing_location_minutes, join_radius_m, privacy_level,
                   team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where id = #{teamId} and deleted = 0
            limit 1
            """)
    Team findById(@Param("teamId") Long teamId);

    /** 查询指定行程对应的公开活跃车队。 */
    @Select("""
            select id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                   start_name, end_name, departure_time, max_member_count, current_member_count,
                   join_mode, recruitment_status, allow_midway_join, deviation_warning_distance_m, deviation_warning_minutes,
                   severe_deviation_distance_m, severe_deviation_minutes, missing_location_minutes, join_radius_m, privacy_level,
                   team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where trip_id = #{tripId} and team_status = 'ACTIVE' and public_flag = 1 and deleted = 0
            limit 1
            """)
    Team findActiveByTripId(@Param("tripId") Long tripId);

    /** 查询指定行程对应的活跃车队，不区分是否公开。 */
    @Select("""
            select id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                   start_name, end_name, departure_time, max_member_count, current_member_count,
                   join_mode, recruitment_status, allow_midway_join, deviation_warning_distance_m, deviation_warning_minutes,
                   severe_deviation_distance_m, severe_deviation_minutes, missing_location_minutes, join_radius_m, privacy_level,
                   team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where trip_id = #{tripId} and team_status = 'ACTIVE' and deleted = 0
            limit 1
            """)
    Team findAnyActiveByTripId(@Param("tripId") Long tripId);

    /**
     * 查询公开、活跃且未满员的车队列表。
     */
    @Select("""
            select id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                   start_name, end_name, departure_time, max_member_count, current_member_count,
                   join_mode, recruitment_status, allow_midway_join, deviation_warning_distance_m, deviation_warning_minutes,
                   severe_deviation_distance_m, severe_deviation_minutes, missing_location_minutes, join_radius_m, privacy_level,
                   team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where public_flag = 1 and team_status = 'ACTIVE' and recruitment_status = 'OPEN' and deleted = 0
              and current_member_count < max_member_count
            order by departure_time asc
            limit #{limit}
            """)
    List<Team> findPublicActive(@Param("limit") Integer limit);

    /**
     * 查询指定用户当前拥有的活跃车队。
     */
    @Select("""
            select id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                   start_name, end_name, departure_time, max_member_count, current_member_count,
                   join_mode, recruitment_status, allow_midway_join, deviation_warning_distance_m, deviation_warning_minutes,
                   severe_deviation_distance_m, severe_deviation_minutes, missing_location_minutes, join_radius_m, privacy_level,
                   team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where owner_user_id = #{userId} and team_status = 'ACTIVE' and deleted = 0
            limit 1
            """)
    Team findActiveOwnedByUser(@Param("userId") Long userId);

    /**
     * 新增车队主表记录。
     */
    @Insert("""
            insert into team
                (id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                 start_name, end_name, departure_time, max_member_count, current_member_count,
                 join_mode, recruitment_status, allow_midway_join, deviation_warning_distance_m, deviation_warning_minutes,
                   severe_deviation_distance_m, severe_deviation_minutes, missing_location_minutes, join_radius_m, privacy_level,
                   team_status, public_flag, chat_conversation_id, notice,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{ownerUserId}, #{ownerVehicleId}, #{teamName}, #{teamDesc},
                 #{startName}, #{endName}, #{departureTime}, #{maxMemberCount}, #{currentMemberCount},
                 #{joinMode}, #{recruitmentStatus}, #{allowMidwayJoin}, #{deviationWarningDistanceM}, #{deviationWarningMinutes},
                 #{severeDeviationDistanceM}, #{severeDeviationMinutes}, #{missingLocationMinutes}, #{joinRadiusM}, #{privacyLevel},
                 #{teamStatus}, #{publicFlag}, #{chatConversationId}, #{notice},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(Team team);

    /**
     * 审批通过入队申请时增加车队人数，并确保车队未满员。
     */
    @Update("""
            update team
            set current_member_count = current_member_count + #{memberCount}, updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
              and current_member_count + #{memberCount} <= max_member_count
            """)
    int incrementMemberCount(
            @Param("teamId") Long teamId,
            @Param("memberCount") Integer memberCount,
            @Param("now") LocalDateTime now);

    /**
     * 成员退出时扣减车队人数，保留队长至少一人。
     */
    @Update("""
            update team
            set current_member_count = greatest(current_member_count - 1, 1), updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
            """)
    void decrementMemberCount(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);

    /** 群主解散群聊时同步解散车队并清空有效名额。 */
    @Update("""
            update team
            set team_status = 'DISSOLVED', current_member_count = 0, updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
            """)
    int dissolve(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);

    /** 队长更新招募状态、途中加入、脱队阈值和隐私级别。 */
    @Update("""
            update team
            set recruitment_status = coalesce(#{recruitmentStatus}, recruitment_status),
                allow_midway_join = coalesce(#{allowMidwayJoin}, allow_midway_join),
                deviation_warning_distance_m = coalesce(#{deviationWarningDistanceM}, deviation_warning_distance_m),
                deviation_warning_minutes = coalesce(#{deviationWarningMinutes}, deviation_warning_minutes),
                severe_deviation_distance_m = coalesce(#{severeDeviationDistanceM}, severe_deviation_distance_m),
                severe_deviation_minutes = coalesce(#{severeDeviationMinutes}, severe_deviation_minutes),
                missing_location_minutes = coalesce(#{missingLocationMinutes}, missing_location_minutes),
                join_radius_m = coalesce(#{joinRadiusM}, join_radius_m),
                privacy_level = coalesce(#{privacyLevel}, privacy_level),
                updated_at = #{now}
            where id = #{teamId} and owner_user_id = #{ownerUserId}
              and team_status = 'ACTIVE' and deleted = 0
            """)
    int updateSettings(@Param("teamId") Long teamId,
                       @Param("ownerUserId") Long ownerUserId,
                       @Param("recruitmentStatus") String recruitmentStatus,
                       @Param("allowMidwayJoin") Integer allowMidwayJoin,
                       @Param("deviationWarningDistanceM") Integer deviationWarningDistanceM,
                       @Param("deviationWarningMinutes") Integer deviationWarningMinutes,
                       @Param("severeDeviationDistanceM") Integer severeDeviationDistanceM,
                       @Param("severeDeviationMinutes") Integer severeDeviationMinutes,
                       @Param("missingLocationMinutes") Integer missingLocationMinutes,
                       @Param("joinRadiusM") Integer joinRadiusM,
                       @Param("privacyLevel") String privacyLevel,
                       @Param("now") LocalDateTime now);

    /** 更新车队名称和公告。 */
    @Update("""
            update team
            set team_name = coalesce(#{teamName}, team_name),
                notice = coalesce(#{notice}, notice),
                updated_at = #{now}
            where id = #{teamId} and owner_user_id = #{ownerUserId}
              and team_status = 'ACTIVE' and deleted = 0
            """)
    int updateProfile(@Param("teamId") Long teamId,
                      @Param("ownerUserId") Long ownerUserId,
                      @Param("teamName") String teamName,
                      @Param("notice") String notice,
                      @Param("now") LocalDateTime now);

}
