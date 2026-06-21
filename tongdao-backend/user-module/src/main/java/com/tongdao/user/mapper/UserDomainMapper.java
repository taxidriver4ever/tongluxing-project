package com.tongdao.user.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserDomainMapper {

    @Select("""
            select p.id, p.user_id userId, p.nickname, p.avatar_image_key avatarImageKey, p.gender, p.birthday,
                   p.city_code cityCode, p.city_name cityName, p.bio, p.profile_status profileStatus,
                   coalesce((select c.certification_status from user_identity_certification c
                             where c.user_id=p.user_id and c.deleted=0 order by c.submitted_at desc limit 1), 'UNSUBMITTED') certificationStatus
            from user_profile p where p.user_id=#{userId} and p.deleted=0 limit 1
            """)
    Map<String, Object> findProfile(@Param("userId") Long userId);

    @Insert("""
            insert into user_profile(id,user_id,nickname,avatar_image_key,gender,birthday,city_code,city_name,bio,profile_status,created_at,updated_at,deleted)
            values(#{id},#{userId},'', '',0,null,'','', '', 'ACTIVE',#{now},#{now},0)
            """)
    int insertProfile(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            update user_profile set nickname=#{nickname},avatar_image_key=#{avatarImageKey},gender=#{gender},birthday=#{birthday},
                city_code=#{cityCode},city_name=#{cityName},bio=#{bio},updated_at=#{now}
            where user_id=#{userId} and deleted=0
            """)
    int updateProfile(@Param("userId") Long userId, @Param("nickname") String nickname,
                      @Param("avatarImageKey") String avatarImageKey, @Param("gender") Integer gender,
                      @Param("birthday") LocalDate birthday, @Param("cityCode") String cityCode,
                      @Param("cityName") String cityName, @Param("bio") String bio, @Param("now") LocalDateTime now);

    @Select("""
            select id,user_id userId,certification_status certificationStatus,reject_reason rejectReason,
                   submitted_at submittedAt,reviewed_at reviewedAt
            from user_identity_certification where user_id=#{userId} and deleted=0 order by submitted_at desc limit 1
            """)
    Map<String, Object> findLatestCertification(@Param("userId") Long userId);

    @Insert("""
            insert into user_identity_certification(id,user_id,real_name_cipher,id_card_no_cipher,driving_license_image_key,
                face_image_key,certification_status,reject_reason,submitted_at,reviewed_at,created_at,updated_at,deleted)
            values(#{id},#{userId},#{realNameCipher},#{idCardCipher},#{licenseKey},#{faceKey},'PENDING','',#{now},#{now},#{now},#{now},0)
            """)
    int insertCertification(@Param("id") Long id, @Param("userId") Long userId,
                            @Param("realNameCipher") String realNameCipher, @Param("idCardCipher") String idCardCipher,
                            @Param("licenseKey") String licenseKey, @Param("faceKey") String faceKey,
                            @Param("now") LocalDateTime now);

    @Select("""
            select user_id userId,profile_visibility profileVisibility,vehicle_visibility vehicleVisibility,
                   invite_enabled_flag inviteEnabledFlag from user_privacy_setting where user_id=#{userId} and deleted=0 limit 1
            """)
    Map<String, Object> findPrivacy(@Param("userId") Long userId);

    @Insert("""
            insert into user_privacy_setting(id,user_id,profile_visibility,vehicle_visibility,invite_enabled_flag,created_at,updated_at,deleted)
            values(#{id},#{userId},'PUBLIC','PUBLIC',1,#{now},#{now},0)
            """)
    int insertPrivacy(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            select total_points totalPoints,level_code levelCode from user_growth_account where user_id=#{userId} and deleted=0 limit 1
            """)
    Map<String, Object> findGrowth(@Param("userId") Long userId);

    @Select("""
            select id,total_points totalPoints,level_code levelCode,version from user_growth_account
            where user_id=#{userId} and deleted=0 limit 1 for update
            """)
    Map<String, Object> findGrowthForUpdate(@Param("userId") Long userId);

    @Insert("""
            insert into user_growth_account(id,user_id,total_points,level_code,version,created_at,updated_at,deleted)
            values(#{id},#{userId},0,'LV1',0,#{now},#{now},0)
            """)
    int insertGrowth(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            select level_code from growth_level_rule where enabled_flag=1 and deleted=0 and min_points<=#{points}
              and (max_points is null or max_points>=#{points}) order by min_points desc limit 1
            """)
    String findLevelCode(@Param("points") Integer points);

    @Update("""
            update user_growth_account set total_points=#{points},level_code=#{levelCode},version=version+1,updated_at=#{now}
            where id=#{id} and version=#{version} and deleted=0
            """)
    int updateGrowth(@Param("id") Long id, @Param("points") Integer points, @Param("levelCode") String levelCode,
                     @Param("version") Integer version, @Param("now") LocalDateTime now);

    @Insert("""
            insert into user_growth_log(id,user_id,biz_type,biz_id,point_delta,balance_after,remark,created_at,updated_at,deleted)
            values(#{id},#{userId},#{bizType},#{bizId},#{delta},#{balance},#{remark},#{now},#{now},0)
            """)
    int insertGrowthLog(@Param("id") Long id, @Param("userId") Long userId, @Param("bizType") String bizType,
                        @Param("bizId") String bizId, @Param("delta") Integer delta, @Param("balance") Integer balance,
                        @Param("remark") String remark, @Param("now") LocalDateTime now);

    @Select("select count(*) from user_growth_log where user_id=#{userId} and biz_type=#{bizType} and deleted=0")
    int countGrowthEvents(@Param("userId") Long userId, @Param("bizType") String bizType);

    @Select("""
            select id badgeId from growth_badge where enabled_flag=1 and deleted=0
              and json_unquote(json_extract(condition_json,'$.eventType'))=#{bizType}
              and cast(json_unquote(json_extract(condition_json,'$.threshold')) as unsigned)<=#{eventCount}
            """)
    List<Long> findEligibleBadgeIds(@Param("bizType") String bizType, @Param("eventCount") Integer eventCount);

    @Insert("""
            insert ignore into user_badge(id,user_id,badge_id,source_biz_id,awarded_at,created_at,updated_at,deleted)
            values(#{id},#{userId},#{badgeId},#{bizId},#{now},#{now},#{now},0)
            """)
    int insertUserBadge(@Param("id") Long id, @Param("userId") Long userId, @Param("badgeId") Long badgeId,
                        @Param("bizId") String bizId, @Param("now") LocalDateTime now);

    @Update("""
            update invite_relation set relation_status='VALID',first_team_completed_at=#{now},updated_at=#{now}
            where invitee_user_id=#{userId} and relation_status='BOUND' and deleted=0
            """)
    int markInviteRelationValid(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("select inviter_user_id from invite_relation where invitee_user_id=#{userId} and deleted=0 limit 1")
    Long findInviterIdByInvitee(@Param("userId") Long userId);

    @Select("""
            select min_points from growth_level_rule where enabled_flag=1 and deleted=0 and min_points>#{points}
            order by min_points limit 1
            """)
    Integer findNextLevelPoints(@Param("points") Integer points);

    @Select("""
            select id,biz_type bizType,biz_id bizId,point_delta pointDelta,balance_after balanceAfter,remark,created_at createdAt
            from user_growth_log where user_id=#{userId} and deleted=0 order by created_at desc limit #{offset},#{size}
            """)
    List<Map<String, Object>> findGrowthLogs(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from user_growth_log where user_id=#{userId} and deleted=0")
    long countGrowthLogs(@Param("userId") Long userId);

    @Select("""
            select b.id badgeId,b.badge_code badgeCode,b.badge_name badgeName,b.badge_image_key badgeImageKey,ub.awarded_at awardedAt
            from growth_badge b join user_badge ub on ub.badge_id=b.id and ub.user_id=#{userId} and ub.deleted=0
            where b.enabled_flag=1 and b.deleted=0 order by ub.awarded_at desc
            """)
    List<Map<String, Object>> findEarnedBadges(@Param("userId") Long userId);

    @Select("""
            select b.id badgeId,b.badge_code badgeCode,b.badge_name badgeName,b.badge_image_key badgeImageKey,null awardedAt
            from growth_badge b where b.enabled_flag=1 and b.deleted=0 and not exists
            (select 1 from user_badge ub where ub.user_id=#{userId} and ub.badge_id=b.id and ub.deleted=0)
            order by b.id
            """)
    List<Map<String, Object>> findLockedBadges(@Param("userId") Long userId);

    @Select("select id,user_id userId,invite_code inviteCode,enabled_flag enabledFlag from invite_code where user_id=#{userId} and deleted=0 limit 1")
    Map<String, Object> findInviteCodeByUser(@Param("userId") Long userId);

    @Select("select id,user_id userId,invite_code inviteCode,enabled_flag enabledFlag from invite_code where invite_code=#{code} and deleted=0 limit 1")
    Map<String, Object> findInviteCode(@Param("code") String code);

    @Insert("""
            insert into invite_code(id,user_id,invite_code,enabled_flag,created_at,updated_at,deleted)
            values(#{id},#{userId},#{code},1,#{now},#{now},0)
            """)
    int insertInviteCode(@Param("id") Long id, @Param("userId") Long userId, @Param("code") String code, @Param("now") LocalDateTime now);

    @Select("select id from invite_relation where invitee_user_id=#{userId} and deleted=0 limit 1")
    Long findInviteRelationIdByInvitee(@Param("userId") Long userId);

    @Select("""
            with recursive ancestors(user_id) as (
                select inviter_user_id from invite_relation where invitee_user_id=#{inviterId} and deleted=0
                union all
                select r.inviter_user_id from invite_relation r join ancestors a on r.invitee_user_id=a.user_id
                where r.deleted=0
            )
            select count(*) from ancestors where user_id=#{inviteeId}
            """)
    int createsInviteCycle(@Param("inviterId") Long inviterId, @Param("inviteeId") Long inviteeId);

    @Insert("""
            insert into invite_relation(id,inviter_user_id,invitee_user_id,invite_code,relation_status,bound_at,first_team_completed_at,created_at,updated_at,deleted)
            values(#{id},#{inviterId},#{inviteeId},#{code},'BOUND',#{now},null,#{now},#{now},0)
            """)
    int insertInviteRelation(@Param("id") Long id, @Param("inviterId") Long inviterId,
                             @Param("inviteeId") Long inviteeId, @Param("code") String code, @Param("now") LocalDateTime now);

    List<Map<String, Object>> findInvitations(@Param("userId") Long userId, @Param("status") String status,
                                               @Param("offset") int offset, @Param("size") int size);

    long countInvitations(@Param("userId") Long userId, @Param("status") String status);

    @Select("select count(*) from invite_relation where inviter_user_id=#{userId} and relation_status='VALID' and deleted=0")
    int countValidInvitations(@Param("userId") Long userId);

    @Select("select rule_code from invite_reward_record where beneficiary_user_id=#{userId} and reward_status='GRANTED' and deleted=0 order by created_at")
    List<String> findGrantedRewardRules(@Param("userId") Long userId);

    List<Map<String, Object>> findCoupons(@Param("userId") Long userId, @Param("status") String status,
                                          @Param("type") String type, @Param("offset") int offset, @Param("size") int size);

    long countCoupons(@Param("userId") Long userId, @Param("status") String status, @Param("type") String type);

    @Select("""
            select uc.id,uc.user_id userId,uc.template_id templateId,ct.coupon_name couponName,ct.coupon_type couponType,
                   ct.issuer_id issuerId,ct.threshold_amount thresholdAmount,ct.discount_amount discountAmount,ct.scope_json scopeJson,
                   uc.coupon_status couponStatus,uc.valid_start_at validStartAt,uc.valid_end_at validEndAt,
                   uc.locked_order_id lockedOrderId,uc.used_order_id usedOrderId
            from user_coupon uc join coupon_template ct on ct.id=uc.template_id
            where uc.id=#{id} and uc.user_id=#{userId} and uc.deleted=0 and ct.deleted=0 limit 1
            """)
    Map<String, Object> findCoupon(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            select uc.id,ct.coupon_name couponName,ct.discount_amount deductionAmount,ct.scope_json scopeJson,uc.valid_end_at validEndAt
            from user_coupon uc join coupon_template ct on ct.id=uc.template_id
            where uc.user_id=#{userId} and uc.coupon_status='AVAILABLE' and uc.valid_start_at<=now() and uc.valid_end_at>now()
              and ct.template_status='ACTIVE' and ct.threshold_amount<=#{amount} and ct.deleted=0 and uc.deleted=0
              and (ct.issuer_id is null or ct.issuer_id=#{merchantId})
            order by ct.discount_amount desc,uc.valid_end_at
            """)
    List<Map<String, Object>> findAvailableCoupons(@Param("userId") Long userId, @Param("merchantId") Long merchantId,
                                                    @Param("amount") BigDecimal amount);

    @Update("""
            update user_coupon uc join coupon_template ct on ct.id=uc.template_id
            set uc.coupon_status='LOCKED',uc.locked_order_id=#{orderId},uc.updated_at=#{now}
            where uc.id=#{id} and uc.coupon_status='AVAILABLE' and uc.valid_start_at<=#{now} and uc.valid_end_at>#{now}
              and ct.template_status='ACTIVE' and ct.threshold_amount<=#{amount} and uc.deleted=0 and ct.deleted=0
            """)
    int lockCoupon(@Param("id") Long id, @Param("orderId") Long orderId, @Param("amount") BigDecimal amount,
                   @Param("now") LocalDateTime now);

    @Select("""
            select uc.user_id userId,ct.discount_amount discountAmount from user_coupon uc join coupon_template ct on ct.id=uc.template_id
            where uc.id=#{id} and uc.locked_order_id=#{orderId} and uc.coupon_status='LOCKED' and uc.deleted=0 limit 1
            """)
    Map<String, Object> findLockedCoupon(@Param("id") Long id, @Param("orderId") Long orderId);

    @Select("select id from user_coupon where locked_order_id=#{orderId} and coupon_status='LOCKED' and deleted=0")
    List<Long> findLockedCouponIdsByOrder(@Param("orderId") Long orderId);

    @Update("""
            update user_coupon set coupon_status='USED',used_order_id=#{orderId},updated_at=#{now}
            where locked_order_id=#{orderId} and coupon_status='LOCKED' and deleted=0
            """)
    int confirmCoupons(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    @Update("""
            update user_coupon set coupon_status='AVAILABLE',locked_order_id=null,updated_at=#{now}
            where locked_order_id=#{orderId} and coupon_status='LOCKED' and valid_end_at>#{now} and deleted=0
            """)
    int releaseCoupons(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    @Select("select count(*) from user_coupon where user_id=#{userId} and coupon_status='AVAILABLE' and valid_end_at>now() and deleted=0")
    int countAvailableCoupons(@Param("userId") Long userId);

    @Select("select count(*) from user_coupon where user_id=#{userId} and coupon_status='AVAILABLE' and valid_end_at between now() and date_add(now(),interval 7 day) and deleted=0")
    int countExpiringCoupons(@Param("userId") Long userId);

    @Insert("""
            insert into trip_draft(id,user_id,start_location_json,end_location_json,waypoint_json,departure_time,duration_days,
                people_count,remark,draft_status,published_trip_id,created_at,updated_at,deleted)
            values(#{id},#{userId},#{startJson},#{endJson},#{waypointJson},#{departureTime},#{durationDays},#{peopleCount},#{remark},'DRAFT',null,#{now},#{now},0)
            """)
    int insertDraft(@Param("id") Long id, @Param("userId") Long userId, @Param("startJson") String startJson,
                    @Param("endJson") String endJson, @Param("waypointJson") String waypointJson,
                    @Param("departureTime") LocalDateTime departureTime, @Param("durationDays") Integer durationDays,
                    @Param("peopleCount") Integer peopleCount, @Param("remark") String remark, @Param("now") LocalDateTime now);

    @Select("""
            select id draftId,user_id userId,start_location_json startJson,end_location_json endJson,waypoint_json waypointJson,
                   departure_time departureTime,duration_days durationDays,people_count peopleCount,remark,draft_status draftStatus,
                   published_trip_id publishedTripId,updated_at updatedAt from trip_draft where id=#{id} and user_id=#{userId} and deleted=0 limit 1
            """)
    Map<String, Object> findDraft(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            select id draftId,user_id userId,start_location_json startJson,end_location_json endJson,waypoint_json waypointJson,
                   departure_time departureTime,duration_days durationDays,people_count peopleCount,remark,draft_status draftStatus,
                   published_trip_id publishedTripId,updated_at updatedAt from trip_draft
            where id=#{id} and user_id=#{userId} and deleted=0 limit 1 for update
            """)
    Map<String, Object> findDraftForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<Map<String, Object>> findDrafts(@Param("userId") Long userId, @Param("status") String status,
                                         @Param("offset") int offset, @Param("size") int size);

    long countDrafts(@Param("userId") Long userId, @Param("status") String status);

    @Update("""
            update trip_draft set start_location_json=#{startJson},end_location_json=#{endJson},waypoint_json=#{waypointJson},
                departure_time=#{departureTime},duration_days=#{durationDays},people_count=#{peopleCount},remark=#{remark},updated_at=#{now}
            where id=#{id} and user_id=#{userId} and draft_status='DRAFT' and deleted=0
            """)
    int updateDraft(@Param("id") Long id, @Param("userId") Long userId, @Param("startJson") String startJson,
                    @Param("endJson") String endJson, @Param("waypointJson") String waypointJson,
                    @Param("departureTime") LocalDateTime departureTime, @Param("durationDays") Integer durationDays,
                    @Param("peopleCount") Integer peopleCount, @Param("remark") String remark, @Param("now") LocalDateTime now);

    @Update("update trip_draft set deleted=1,updated_at=#{now} where id=#{id} and user_id=#{userId} and draft_status='DRAFT' and deleted=0")
    int deleteDraft(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            update trip_draft set draft_status='PUBLISHED',published_trip_id=#{publishedId},updated_at=#{now}
            where id=#{id} and user_id=#{userId} and draft_status='DRAFT' and deleted=0
            """)
    int markDraftPublished(@Param("id") Long id, @Param("userId") Long userId,
                           @Param("publishedId") Long publishedId, @Param("now") LocalDateTime now);

    @Select("""
            select id draftId,user_id userId,start_location_json startJson,end_location_json endJson,waypoint_json waypointJson,
                   departure_time departureTime,duration_days durationDays,people_count peopleCount,remark,draft_status draftStatus,
                   published_trip_id publishedTripId,updated_at updatedAt from trip_draft
            where user_id=#{userId} and draft_status='DRAFT' and deleted=0 order by updated_at desc limit 1
            """)
    Map<String, Object> findLatestDraft(@Param("userId") Long userId);
}
