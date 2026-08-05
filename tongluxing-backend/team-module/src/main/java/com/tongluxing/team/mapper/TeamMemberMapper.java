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
            select id, team_id, user_id, vehicle_id, linked_owner_user_id, linked_vehicle_id, plate_reference, owner_confirm_status, removed_by_user_id, removed_reason, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where team_id = #{teamId} and member_status = 'ACTIVE' and deleted = 0
            order by joined_at asc
            """)
    List<TeamMember> findActiveByTeamId(@Param("teamId") Long teamId);

    /**
     * 统计指定车队的有效成员数。
     *
     * <p>发现行程的人数和剩余名额必须以成员事实表为准，避免缓存计数未及时同步时
     * 已退出成员仍然占用名额。</p>
     */
    @Select("""
            select count(*)
            from team_member
            where team_id = #{teamId} and member_status = 'ACTIVE' and deleted = 0
            """)
    int countActiveByTeamId(@Param("teamId") Long teamId);

    /**
     * 统计同一辆车已经关联的有效成员数。
     *
     * <p>司机成员通过 vehicle_id 关联车辆，乘客通过 linked_vehicle_id 关联车辆；
     * 两者都计入座位占用，避免确认乘客后超过行驶证记录的座位数。</p>
     */
    @Select("""
            select count(*)
            from team_member
            where team_id = #{teamId}
              and member_status = 'ACTIVE' and deleted = 0
              and (vehicle_id = #{vehicleId} or linked_vehicle_id = #{vehicleId})
            """)
    int countActiveByVehicle(@Param("teamId") Long teamId, @Param("vehicleId") Long vehicleId);

    /**
     * 查询指定用户当前所在的全部活跃车队成员记录。
     * 用户可以同时保留自己担任队长的车队，并作为队员加入一个外部车队，
     * 因此这里返回列表，由服务层区分 OWNER 与外部成员关系。
     */
    @Select("""
            select id, team_id, user_id, vehicle_id, linked_owner_user_id, linked_vehicle_id, plate_reference, owner_confirm_status, removed_by_user_id, removed_reason, member_role, member_status, joined_at,
                   exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted
            from team_member
            where user_id = #{userId} and member_status = 'ACTIVE' and deleted = 0
            order by joined_at asc
            """)
    List<TeamMember> findActiveListByUserId(@Param("userId") Long userId);

    /**
     * 查询某用户在指定车队中的历史或当前成员记录。
     */
    @Select("""
            select id, team_id, user_id, vehicle_id, linked_owner_user_id, linked_vehicle_id, plate_reference, owner_confirm_status, removed_by_user_id, removed_reason, member_role, member_status, joined_at,
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
                (id, team_id, user_id, vehicle_id, linked_owner_user_id, linked_vehicle_id, plate_reference, owner_confirm_status, removed_by_user_id, removed_reason, member_role, member_status, joined_at,
                 exited_at, nickname_snapshot, vehicle_snapshot, created_at, updated_at, deleted)
            values
                (#{id}, #{teamId}, #{userId}, #{vehicleId}, #{linkedOwnerUserId}, #{linkedVehicleId}, #{plateReference}, #{ownerConfirmStatus}, #{removedByUserId}, #{removedReason}, #{memberRole}, #{memberStatus}, #{joinedAt},
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

    /** 解散车队时将包括队长在内的全部有效成员标记为退出。 */
    @Update("""
            update team_member
            set member_status = 'EXITED', exited_at = #{now}, updated_at = #{now}
            where team_id = #{teamId} and member_status = 'ACTIVE' and deleted = 0
            """)
    int exitAll(@Param("teamId") Long teamId, @Param("now") LocalDateTime now);

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

    /** 队长移除成员并保留原因，供归队申请判断。 */
    @Update("""
            update team_member
            set member_status = 'REMOVED', exited_at = #{now},
                removed_by_user_id = #{ownerUserId}, removed_reason = #{reason}, updated_at = #{now}
            where team_id = #{teamId} and user_id = #{userId}
              and member_role <> 'OWNER' and member_status = 'ACTIVE' and deleted = 0
            """)
    int removeByOwner(@Param("teamId") Long teamId, @Param("userId") Long userId,
                      @Param("ownerUserId") Long ownerUserId, @Param("reason") String reason,
                      @Param("now") LocalDateTime now);

    /** 保存乘客与队内车辆/车主的关联及确认状态。 */
    @Update("""
            update team_member
            set linked_owner_user_id = #{linkedOwnerUserId}, linked_vehicle_id = #{linkedVehicleId},
                plate_reference = #{plateReference}, owner_confirm_status = #{confirmStatus}, updated_at = #{now}
            where team_id = #{teamId} and user_id = #{userId} and deleted = 0
            """)
    int updateVehicleLink(@Param("teamId") Long teamId, @Param("userId") Long userId,
                          @Param("linkedOwnerUserId") Long linkedOwnerUserId,
                          @Param("linkedVehicleId") Long linkedVehicleId,
                          @Param("plateReference") String plateReference,
                          @Param("confirmStatus") String confirmStatus,
                          @Param("now") LocalDateTime now);

    /** 被关联车主确认或拒绝乘客同车关系。 */
    @Update("""
            update team_member
            set owner_confirm_status = #{status}, updated_at = #{now}
            where team_id = #{teamId} and user_id = #{passengerUserId}
              and linked_owner_user_id = #{ownerUserId} and member_status = 'ACTIVE' and deleted = 0
            """)
    int confirmVehicleLink(@Param("teamId") Long teamId,
                           @Param("passengerUserId") Long passengerUserId,
                           @Param("ownerUserId") Long ownerUserId,
                           @Param("status") String status,
                           @Param("now") LocalDateTime now);

}
