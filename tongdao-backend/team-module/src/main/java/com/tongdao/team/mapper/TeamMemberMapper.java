package com.tongdao.team.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.team.entity.TeamMember;

@Mapper
public interface TeamMemberMapper {

    @Select("""
            select id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where team_id = #{teamId} and member_status = 'ACTIVE' and deleted = 0
            order by joined_at asc
            """)
    List<TeamMember> findActiveByTeamId(@Param("teamId") Long teamId);

    @Select("""
            select id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where user_id = #{userId} and member_status = 'ACTIVE' and deleted = 0
            limit 1
            """)
    TeamMember findActiveByUserId(@Param("userId") Long userId);

    @Select("""
            select id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where team_id = #{teamId} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    TeamMember findByTeamAndUser(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Insert("""
            insert into team_member
                (id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                 exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted)
            values
                (#{id}, #{teamId}, #{userId}, #{vehicleId}, #{memberRole}, #{memberStatus}, #{joinedAt},
                 #{exitedAt}, #{nicknameSnapshot}, #{vehicleSnapshot}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(TeamMember member);

    @Update("""
            update team_member
            set member_status = 'EXITED', exited_at = #{now}, updated_at = #{now}
            where team_id = #{teamId} and user_id = #{userId}
              and member_status = 'ACTIVE' and deleted = 0
            """)
    int exit(@Param("teamId") Long teamId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            update team_member
            set vehicle_id = #{vehicleId},
                member_role = #{role},
                member_status = 'ACTIVE',
                joined_at = #{now},
                exited_at = null,
                updated_at = #{now}
            where team_id = #{teamId} and user_id = #{userId} and deleted = 0
            """)
    int reactivate(@Param("teamId") Long teamId,
                   @Param("userId") Long userId,
                   @Param("vehicleId") Long vehicleId,
                   @Param("role") String role,
                   @Param("now") LocalDateTime now);
}
