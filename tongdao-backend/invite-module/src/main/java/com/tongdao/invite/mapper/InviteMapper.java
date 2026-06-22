package com.tongdao.invite.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.tongdao.invite.dto.InviteQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InviteMapper {
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

    @Select("""
            select id
            from invite_relation
            where invitee_user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    Long findRelationIdByInvitee(Long userId);

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

    @Insert("""
            insert into invite_relation(
                id, inviter_user_id, invitee_user_id, invite_code, relation_status,
                bound_at, first_team_completed_at, created_at, updated_at, deleted
            )
            values (
                #{id}, #{inviterId}, #{inviteeId}, #{code}, 'BOUND',
                #{now}, null, #{now}, #{now}, 0
            )
            """)
    int insertRelation(@Param("id") Long id, @Param("inviterId") Long inviterId,
                       @Param("inviteeId") Long inviteeId, @Param("code") String code,
                       @Param("now") LocalDateTime now);

    List<InviteQueryDTO> findRecords(@Param("userId") Long userId, @Param("status") String status,
                                     @Param("offset") int offset, @Param("size") int size);

    long countRecords(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            select count(*)
            from invite_relation
            where inviter_user_id = #{userId}
              and relation_status = 'VALID'
              and deleted = 0
            """)
    int countValid(Long userId);

    @Select("""
            select rule_code
            from invite_reward_record
            where beneficiary_user_id = #{userId}
              and reward_status = 'GRANTED'
              and deleted = 0
            order by created_at
            """)
    List<String> findGrantedRules(Long userId);

    @Select("""
            select id relationId,
                   inviter_user_id inviterUserId
            from invite_relation
            where invitee_user_id = #{userId}
              and deleted = 0
            limit 1 for update
            """)
    InviteQueryDTO findRelationForUpdate(Long userId);

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
