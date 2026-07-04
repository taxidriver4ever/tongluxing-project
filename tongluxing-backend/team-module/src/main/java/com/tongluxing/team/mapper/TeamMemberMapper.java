package com.tongluxing.team.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.team.entity.TeamMember;

/**
 * 车队成员 Mapper。
 */
@Mapper
public interface TeamMemberMapper {

    /**
     * 查询指定车队的活跃成员。
     */
    @Select("""
            select id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where team_id = #{teamId} and member_status = 'ACTIVE' and deleted = 0
            order by joined_at asc
            """)
    List<TeamMember> findActiveByTeamId(@Param("teamId") Long teamId);

    /**
     * 查询指定用户当前所在的活跃车队成员记录。
     */
    @Select("""
            select id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where user_id = #{userId} and member_status = 'ACTIVE' and deleted = 0
            limit 1
            """)
    TeamMember findActiveByUserId(@Param("userId") Long userId);

    /**
     * 查询某用户在指定车队中的历史或当前成员记录。
     */
    @Select("""
            select id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where team_id = #{teamId} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    TeamMember findByTeamAndUser(@Param("teamId") Long teamId, @Param("userId") Long userId);

    /**
     * 新增车队成员记录。
     */
    @Insert("""
            insert into team_member
                (id, team_id, user_id, vehicle_id, member_role, member_status, joined_at,
                 exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted)
            values
                (#{id}, #{teamId}, #{userId}, #{vehicleId}, #{memberRole}, #{memberStatus}, #{joinedAt},
                 #{exitedAt}, #{nicknameSnapshot}, #{vehicleSnapshot}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(TeamMember member);

    /**
     * 将活跃成员标记为已退出。
     */
    @Update("""
            update team_member
            set member_status = 'EXITED', exited_at = #{now}, updated_at = #{now}
            where team_id = #{teamId} and user_id = #{userId}
              and member_status = 'ACTIVE' and deleted = 0
            """)
    int exit(@Param("teamId") Long teamId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 将历史成员记录重新激活，用于用户重新加入同一车队。
     */
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
