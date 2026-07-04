package com.tongluxing.invite.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.tongluxing.invite.dto.InviteQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 邀请模块 MyBatis Mapper。
 *
 * <p>负责邀请码、邀请关系、邀请奖励记录的读写；分页查询放在 XML 中，其他简单 SQL 直接使用注解。</p>
 */
@Mapper
public interface InviteMapper {

    /** 根据用户 ID 查询该用户的邀请码。 */
    @Select("""
            select id,
                   user_id userId,
                   invite_code inviteCode,
                   enabled_flag enabledFlag
            from invite_code
            where user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    InviteQueryDTO findCodeByUser(Long userId);

    /** 根据邀请码查询有效的邀请码归属人。 */
    @Select("""
            select id,
                   user_id userId,
                   invite_code inviteCode,
                   enabled_flag enabledFlag
            from invite_code
            where invite_code = #{code}
              and enabled_flag = 1
              and deleted = 0
            limit 1
            """)
    InviteQueryDTO findCode(String code);

    /** 为用户创建邀请码，默认启用且未删除。 */
    @Insert("""
            insert into invite_code(
                id, user_id, invite_code, enabled_flag,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{userId}, #{code}, 1,
                #{now}, #{now}, 0
            )
            """)
    int insertCode(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("code") String code, @Param("now") LocalDateTime now);

    /** 查询某个用户是否已经作为被邀请人绑定过邀请关系。 */
    @Select("""
            select id
            from invite_relation
            where invitee_user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    Long findRelationIdByInvitee(Long userId);

    /** 查询某个用户已经绑定的邀请关系，用于“第一条关系为准”的返回。 */
    @Select("""
            select id relationId,
                   inviter_user_id inviterUserId,
                   invitee_user_id inviteeUserId,
                   invite_code inviteCode,
                   relation_status status,
                   bind_source bindSource,
                   bind_source_value bindSourceValue,
                   invitee_registered_at inviteeRegisteredAt,
                   bound_at boundAt,
                   first_team_completed_at firstTeamCompletedAt
            from invite_relation
            where invitee_user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    InviteQueryDTO findRelationByInvitee(Long userId);

    /**
     * 判断新增邀请关系是否会形成循环。
     *
     * <p>例如 A 邀请 B、B 邀请 C 后，不允许 C 再邀请 A。</p>
     */
    @Select("""
            with recursive ancestors(user_id) as (
                select inviter_user_id
                from invite_relation
                where invitee_user_id = #{inviterId}
                  and deleted = 0
                union all
                select r.inviter_user_id
                from invite_relation r
                join ancestors a on r.invitee_user_id = a.user_id
                where r.deleted = 0
            )
            select count(*)
            from ancestors
            where user_id = #{inviteeId}
            """)
    int createsCycle(@Param("inviterId") Long inviterId, @Param("inviteeId") Long inviteeId);

    /** 新增邀请关系，初始状态为 BOUND，等待被邀请人完成有效行为。 */
    @Insert("""
            insert into invite_relation(
                id, inviter_user_id, invitee_user_id, invite_code, relation_status,
                bind_source, bind_source_value, invitee_registered_at,
                bound_at, first_team_completed_at, created_at, updated_at, deleted
            )
            values (
                #{id}, #{inviterId}, #{inviteeId}, #{code}, 'BOUND',
                #{bindSource}, #{bindSourceValue}, #{registeredAt},
                #{now}, null, #{now}, #{now}, 0
            )
            """)
    int insertRelation(@Param("id") Long id, @Param("inviterId") Long inviterId,
                       @Param("inviteeId") Long inviteeId, @Param("code") String code,
                       @Param("bindSource") String bindSource,
                       @Param("bindSourceValue") String bindSourceValue,
                       @Param("registeredAt") LocalDateTime registeredAt,
                       @Param("now") LocalDateTime now);

    /** 分页查询某个邀请人的邀请记录。 */
    List<InviteQueryDTO> findRecords(@Param("userId") Long userId, @Param("status") String status,
                                     @Param("offset") int offset, @Param("size") int size);

    /** 统计某个邀请人的邀请记录总数。 */
    long countRecords(@Param("userId") Long userId, @Param("status") String status);

    /** 统计已经转为 VALID 的有效邀请数量。 */
    @Select("""
            select count(*)
            from invite_relation
            where inviter_user_id = #{userId}
              and relation_status = 'VALID'
              and deleted = 0
            """)
    int countValid(Long userId);

    /** 查询已成功发放的奖励规则编码，用于前端展示奖励进度。 */
    @Select("""
            select rule_code
            from invite_reward_record
            where beneficiary_user_id = #{userId}
              and reward_status = 'GRANTED'
              and deleted = 0
            order by created_at
            """)
    List<String> findGrantedRules(Long userId);

    /** 锁定被邀请人的邀请关系，防止并发完成事件重复处理。 */
    @Select("""
            select id relationId,
                   inviter_user_id inviterUserId
            from invite_relation
            where invitee_user_id = #{userId}
              and deleted = 0
            limit 1 for update
            """)
    InviteQueryDTO findRelationForUpdate(Long userId);

    /** 将邀请关系从 BOUND 标记为 VALID，并记录首次完成组队时间。 */
    @Update("""
            update invite_relation
            set relation_status = 'VALID',
                first_team_completed_at = #{now},
                updated_at = #{now}
            where id = #{id}
              and relation_status = 'BOUND'
              and deleted = 0
            """)
    int markValid(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 插入待发放的邀请奖励记录。 */
    @Insert("""
            insert into invite_reward_record(
                id, relation_id, beneficiary_user_id, rule_code, reward_biz_no,
                reward_snapshot_json, reward_status, failure_reason, granted_at,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{relationId}, #{userId}, #{ruleCode}, #{bizNo},
                #{snapshot}, 'PENDING', null, null,
                #{now}, #{now}, 0
            )
            """)
    int insertReward(@Param("id") Long id, @Param("relationId") Long relationId,
                     @Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                     @Param("bizNo") String bizNo, @Param("snapshot") String snapshot,
                     @Param("now") LocalDateTime now);

    /** 更新邀请奖励发放状态。 */
    @Update("""
            update invite_reward_record
            set reward_status = #{status},
                failure_reason = #{reason},
                granted_at = #{grantedAt},
                updated_at = #{now}
            where reward_biz_no = #{bizNo}
              and deleted = 0
            """)
    int updateReward(@Param("bizNo") String bizNo, @Param("status") String status,
                     @Param("reason") String reason, @Param("grantedAt") LocalDateTime grantedAt,
                     @Param("now") LocalDateTime now);
}
