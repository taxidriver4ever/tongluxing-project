package com.tongdao.team.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.team.entity.Team;

@Mapper
public interface TeamMapper {

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

    @Update("""
            update team
            set current_member_count = current_member_count + 1, updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
              and current_member_count < max_member_count
            """)
    int incrementMemberCount(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);

    @Update("""
            update team
            set current_member_count = greatest(current_member_count - 1, 1), updated_at = #{now}
            where id = #{teamId} and team_status = 'ACTIVE' and deleted = 0
            """)
    void decrementMemberCount(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);
}
