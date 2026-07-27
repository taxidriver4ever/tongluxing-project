package com.tongluxing.invite.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.tongluxing.invite.dto.InviteQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 邀请模块 MyBatis Mapper。 */
@Mapper
public interface InviteMapper {

    @Select("""
            select id, user_id userId, invite_code inviteCode, enabled_flag enabledFlag
            from invite_code
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    InviteQueryDTO findCodeByUser(Long userId);

    @Select("""
            select id, user_id userId, invite_code inviteCode, enabled_flag enabledFlag
            from invite_code
            where invite_code = #{code} and deleted = 0
            limit 1
            """)
    InviteQueryDTO findAnyCode(String code);

    @Select("""
            select id, user_id userId, invite_code inviteCode, enabled_flag enabledFlag
            from invite_code
            where invite_code = #{code} and enabled_flag = 1 and deleted = 0
            limit 1
            """)
    InviteQueryDTO findCode(String code);

    @Insert("""
            insert into invite_code(id, user_id, invite_code, enabled_flag, created_at, updated_at, deleted)
            values (#{id}, #{userId}, #{code}, 1, #{now}, #{now}, 0)
            """)
    int insertCode(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("code") String code, @Param("now") LocalDateTime now);

    @Select("""
            select a.created_at registeredAt
            from auth_account a
            where a.user_id = #{userId} and a.deleted = 0
            limit 1
            """)
    InviteQueryDTO findRegistration(Long userId);

    @Select("""
            select coalesce(nullif(p.nickname, ''), '同路行用户') inviterNickname,
                   p.avatar_image_key inviterAvatarUrl
            from user_profile p
            where p.user_id = #{userId} and p.deleted = 0
            limit 1
            """)
    InviteQueryDTO findInviterProfile(Long userId);

    @Select("""
            select r.id relationId,
                   r.inviter_user_id inviterUserId,
                   r.invitee_user_id inviteeUserId,
                   r.invite_code inviteCode,
                   r.relation_status status,
                   r.bind_source bindSource,
                   r.bind_source_value bindSourceValue,
                   r.invitee_registered_at inviteeRegisteredAt,
                   r.request_id requestId,
                   r.bound_at boundAt,
                   r.first_team_completed_at firstTeamCompletedAt,
                   coalesce(nullif(p.nickname, ''), '同路行用户') inviterNickname,
                   p.avatar_image_key inviterAvatarUrl
            from invite_relation r
            left join user_profile p on p.user_id = r.inviter_user_id and p.deleted = 0
            where r.invitee_user_id = #{userId} and r.deleted = 0
            limit 1
            """)
    InviteQueryDTO findRelationByInvitee(Long userId);

    @Select("""
            select r.id relationId, r.inviter_user_id inviterUserId,
                   r.invitee_user_id inviteeUserId, r.invite_code inviteCode,
                   r.relation_status status, r.bind_source bindSource,
                   r.request_id requestId, r.bound_at boundAt,
                   r.first_team_completed_at firstTeamCompletedAt,
                   coalesce(nullif(p.nickname, ''), '同路行用户') inviterNickname,
                   p.avatar_image_key inviterAvatarUrl
            from invite_relation r
            left join user_profile p on p.user_id=r.inviter_user_id and p.deleted=0
            where r.request_id=#{requestId} and r.deleted=0
            limit 1
            """)
    InviteQueryDTO findRelationByRequestId(String requestId);

    @Select("""
            with recursive ancestors(user_id) as (
                select inviter_user_id
                from invite_relation
                where invitee_user_id = #{inviterId} and deleted = 0
                union all
                select r.inviter_user_id
                from invite_relation r
                join ancestors a on r.invitee_user_id = a.user_id
                where r.deleted = 0
            )
            select count(*) from ancestors where user_id = #{inviteeId}
            """)
    int createsCycle(@Param("inviterId") Long inviterId, @Param("inviteeId") Long inviteeId);

    @Insert("""
            insert into invite_relation(
                id, inviter_user_id, invitee_user_id, invite_code, relation_status,
                bind_source, bind_source_value, request_id, invitee_registered_at,
                bound_at, first_team_completed_at, created_at, updated_at, deleted
            ) values (
                #{id}, #{inviterId}, #{inviteeId}, #{code}, 'REGISTERED',
                #{sourceType}, #{code}, #{requestId}, #{registeredAt},
                #{now}, null, #{now}, #{now}, 0
            )
            """)
    int insertRelation(@Param("id") Long id,
                       @Param("inviterId") Long inviterId,
                       @Param("inviteeId") Long inviteeId,
                       @Param("code") String code,
                       @Param("sourceType") String sourceType,
                       @Param("requestId") String requestId,
                       @Param("registeredAt") LocalDateTime registeredAt,
                       @Param("now") LocalDateTime now);

    List<InviteQueryDTO> findRecords(@Param("userId") Long userId, @Param("status") String status,
                                     @Param("offset") int offset, @Param("size") int size);

    long countRecords(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            select count(*) from invite_relation
            where inviter_user_id = #{userId} and deleted = 0
            """)
    int countInvitees(Long userId);

    @Select("""
            select count(*) from invite_relation
            where inviter_user_id = #{userId} and relation_status = 'VALID' and deleted = 0
            """)
    int countValid(Long userId);

    @Select("""
            select rule_code from invite_reward_record
            where beneficiary_user_id = #{userId} and reward_status = 'ISSUED' and deleted = 0
            order by created_at
            """)
    List<String> findGrantedRules(Long userId);

    @Select("""
            select id relationId, inviter_user_id inviterUserId,
                   first_team_completed_at firstTeamCompletedAt
            from invite_relation
            where invitee_user_id = #{userId} and deleted = 0
            limit 1 for update
            """)
    InviteQueryDTO findRelationForUpdate(Long userId);

    @Update("""
            update invite_relation
            set relation_status = 'VALID', first_team_completed_at = #{now}, updated_at = #{now}
            where id = #{id} and relation_status in ('REGISTERED', 'BOUND')
              and first_team_completed_at is null and deleted = 0
            """)
    int markValid(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Insert("""
            insert into invite_reward_record(
                id, relation_id, beneficiary_user_id, inviter_user_id, invitee_user_id,
                rule_code, reward_rule_code, reward_type, reward_value,
                reward_biz_no, idempotency_key, reward_snapshot_json, reward_status,
                triggered_at, failure_reason, granted_at, issued_at, created_at, updated_at, deleted
            )
            select #{id}, r.id, #{userId}, r.inviter_user_id, r.invitee_user_id,
                   #{ruleCode}, #{ruleCode}, coalesce(rr.reward_type, 'GROWTH_VALUE'),
                   coalesce(rr.reward_value, 0), #{bizNo}, #{bizNo}, #{snapshot}, 'PENDING',
                   #{now}, null, null, null, #{now}, #{now}, 0
            from invite_relation r
            left join invite_reward_rule rr
              on rr.rule_code=#{ruleCode} and rr.status='ENABLED' and rr.deleted=0
            where r.id=#{relationId} and r.deleted=0
            """)
    int insertReward(@Param("id") Long id, @Param("relationId") Long relationId,
                     @Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                     @Param("bizNo") String bizNo, @Param("snapshot") String snapshot,
                     @Param("now") LocalDateTime now);


    @Select("""
            select reward_status from invite_reward_record
            where reward_biz_no=#{bizNo} and deleted=0 limit 1
            """)
    String findRewardStatus(String bizNo);

    @Select("""
            select reward_value from invite_reward_rule
            where rule_code=#{ruleCode} and status='ENABLED' and deleted=0
            limit 1
            """)
    Integer findRewardRuleValue(String ruleCode);

    @Update("""
            update invite_reward_record
            set reward_status = #{status}, failure_reason = #{reason},
                granted_at = #{grantedAt}, issued_at = #{grantedAt}, updated_at = #{now}
            where reward_biz_no = #{bizNo} and deleted = 0
            """)
    int updateReward(@Param("bizNo") String bizNo, @Param("status") String status,
                     @Param("reason") String reason, @Param("grantedAt") LocalDateTime grantedAt,
                     @Param("now") LocalDateTime now);
}
