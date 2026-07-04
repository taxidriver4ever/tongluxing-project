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
                   join_mode, team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where id = #{teamId} and deleted = 0
            limit 1
            """)
    Team findById(@Param("teamId") Long teamId);

    /**
     * 查询公开、活跃且未满员的车队列表。
     */
    @Select("""
            select id, trip_id, owner_user_id, owner_vehicle_id, team_name, team_desc,
                   start_name, end_name, departure_time, max_member_count, current_member_count,
                   join_mode, team_status, public_flag, chat_conversation_id, notice,
                   created_at, updated_at, deleted
            from team
            where public_flag = 1 and team_status = 'ACTIVE' and deleted = 0
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
                   join_mode, team_status, public_flag, chat_conversation_id, notice,
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
                 join_mode, team_status, public_flag, chat_conversation_id, notice,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{tripId}, #{ownerUserId}, #{ownerVehicleId}, #{teamName}, #{teamDesc},
                 #{startName}, #{endName}, #{departureTime}, #{maxMemberCount}, #{currentMemberCount},
                 #{joinMode}, #{teamStatus}, #{publicFlag}, #{chatConversationId}, #{notice},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(Team team);

    /**
     * 审批通过入队申请时增加车队人数，并确保车队未满员。
     */
    @Update("""
            update team
            set current_member_count = current_member_count + 1, updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
              and current_member_count < max_member_count
            """)
    int incrementMemberCount(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);

    /**
     * 成员退出时扣减车队人数，保留队长至少一人。
     */
    @Update("""
            update team
            set current_member_count = greatest(current_member_count - 1, 1), updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
            """)
    void decrementMemberCount(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);
}
