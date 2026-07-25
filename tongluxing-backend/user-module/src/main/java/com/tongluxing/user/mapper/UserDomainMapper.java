package com.tongluxing.user.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.tongluxing.user.dto.UserQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户域数据库访问接口。
 *
 * <p>集中管理用户资料、驾驶证认证记录和隐私设置的读写操作。
 * 该 Mapper 只负责数据访问，业务上的缓存、加密、权限判断由 Service 层处理。</p>
 */
@Mapper
public interface UserDomainMapper {

    /**
     * 查询用户资料，并附带最新驾驶证认证状态。
     */
    @Select("""
            select p.id, p.user_id userId, p.tongluxing_id tongluxingId, p.nickname, p.avatar_image_key avatarImageKey, p.gender, p.birthday,
                   p.city_code cityCode, p.city_name cityName, p.bio, p.profile_status profileStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,
                   coalesce(s.total_duration_minutes,0) totalDurationMinutes,
                   coalesce(s.completed_waypoint_count,0) completedWaypointCount,
                   coalesce((select c.certification_status from user_driving_license_certification c
                             where c.user_id=p.user_id and c.deleted=0 order by c.submitted_at desc limit 1), 'UNSUBMITTED') certificationStatus
            from user_profile p left join user_statistics s on s.user_id=p.user_id
            where p.user_id=#{userId} and p.deleted=0 limit 1
            """)
    UserQueryDTO findProfile(@Param("userId") Long userId);

    /** 搜索公开用户资料，供“发起人”标签分页展示。 */
    @Select("""
            <script>
            select p.id, p.user_id userId, p.tongluxing_id tongluxingId, p.nickname, p.avatar_image_key avatarImageKey,
                   p.city_name cityName, p.bio, p.profile_status profileStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,
                   coalesce((select c.certification_status from user_driving_license_certification c
                             where c.user_id=p.user_id and c.deleted=0 order by c.submitted_at desc limit 1),
                            'UNSUBMITTED') certificationStatus
            from user_profile p
            join user_privacy_setting privacy on privacy.user_id=p.user_id and privacy.deleted=0
                 and privacy.profile_visibility='PUBLIC'
            left join user_statistics s on s.user_id=p.user_id
            where p.deleted=0 and p.profile_status='ACTIVE' and p.user_id &lt;&gt; #{excludeUserId}
            <if test='keyword != null and keyword != ""'>
              and (lower(p.nickname) like concat('%',lower(#{keyword}),'%')
                   or lower(p.city_name) like concat('%',lower(#{keyword}),'%')
                   or lower(p.bio) like concat('%',lower(#{keyword}),'%')
                   or lower(p.tongluxing_id) like concat('%',lower(#{keyword}),'%')
                   or cast(p.user_id as char)=#{keyword})
            </if>
            order by coalesce(s.total_trip_count,0) desc, p.updated_at desc
            limit #{offset},#{size}
            </script>
            """)
    List<UserQueryDTO> searchPublicProfiles(@Param("keyword") String keyword,
                                            @Param("excludeUserId") Long excludeUserId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    /**
     * 创建用户默认资料。
     *
     * <p>默认资料在用户首次访问用户模块能力时懒初始化。</p>
     */
    @Insert("""
            insert into user_profile(id,user_id,tongluxing_id,nickname,avatar_image_key,gender,birthday,city_code,city_name,bio,profile_status,created_at,updated_at,deleted)
            values(#{id},#{userId},#{tongluxingId},'', '',0,null,'','', '', 'ACTIVE',#{now},#{now},0)
            """)
    int insertProfile(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("tongluxingId") String tongluxingId, @Param("now") LocalDateTime now);

    /** 为迁移前的历史资料补齐不可变的同路行号。 */
    @Update("""
            update user_profile
            set tongluxing_id=#{tongluxingId}, updated_at=#{now}
            where user_id=#{userId} and deleted=0
              and (tongluxing_id is null or tongluxing_id='')
            """)
    int updateTongluxingId(@Param("userId") Long userId,
                           @Param("tongluxingId") String tongluxingId,
                           @Param("now") LocalDateTime now);

    /**
     * 更新用户个人资料。
     */
    @Update("""
            update user_profile
            set nickname = #{nickname},
                avatar_image_key = #{avatarImageKey},
                gender = #{gender},
                birthday = #{birthday},
                city_code = #{cityCode},
                city_name = #{cityName},
                bio = #{bio},
                updated_at = #{now}
            where user_id = #{userId}
              and deleted = 0
            """)
    int updateProfile(@Param("userId") Long userId, @Param("nickname") String nickname,
                      @Param("avatarImageKey") String avatarImageKey, @Param("gender") Integer gender,
                      @Param("birthday") LocalDate birthday, @Param("cityCode") String cityCode,
                      @Param("cityName") String cityName, @Param("bio") String bio, @Param("now") LocalDateTime now);

    /**
     * 查询用户最新一条驾驶证认证记录。
     */
    @Select("""
            select id,user_id userId,certification_status certificationStatus,reject_reason rejectReason,
                   submitted_at submittedAt,reviewed_at reviewedAt
            from user_driving_license_certification where user_id=#{userId} and deleted=0 order by submitted_at desc limit 1
            """)
    UserQueryDTO findLatestCertification(@Param("userId") Long userId);

    /** 新增驾驶证认证申请记录。 */
    @Insert("""
            insert into user_driving_license_certification(
                id,user_id,holder_name_cipher,license_no_cipher,license_no_mask,vehicle_class,
                first_issue_date,valid_from,valid_to,issuing_authority,license_front_image_key,
                license_back_image_key,recognition_source,certification_status,reject_reason,
                reviewer_id,submitted_at,reviewed_at,created_at,updated_at,deleted)
            values(#{id},#{userId},#{holderNameCipher},#{licenseNoCipher},#{licenseNoMask},#{vehicleClass},
                #{firstIssueDate},#{validFrom},#{validTo},#{issuingAuthority},#{licenseFrontImageKey},
                #{licenseBackImageKey},#{recognitionSource},'PENDING',null,null,#{now},null,#{now},#{now},0)
            """)
    int insertCertification(@Param("id") Long id, @Param("userId") Long userId,
                            @Param("holderNameCipher") String holderNameCipher,
                            @Param("licenseNoCipher") String licenseNoCipher,
                            @Param("licenseNoMask") String licenseNoMask,
                            @Param("vehicleClass") String vehicleClass,
                            @Param("firstIssueDate") LocalDate firstIssueDate,
                            @Param("validFrom") LocalDate validFrom,
                            @Param("validTo") LocalDate validTo,
                            @Param("issuingAuthority") String issuingAuthority,
                            @Param("licenseFrontImageKey") String licenseFrontImageKey,
                            @Param("licenseBackImageKey") String licenseBackImageKey,
                            @Param("recognitionSource") String recognitionSource,
                            @Param("now") LocalDateTime now);

    /** 分页查询后台驾驶证认证列表。 */
    @Select("""
            <script>
            select id,user_id userId,holder_name_cipher holderNameCipher,license_no_mask licenseNoMask,
                   vehicle_class vehicleClass,valid_to validTo,certification_status certificationStatus,
                   submitted_at submittedAt
            from user_driving_license_certification
            where deleted=0
            <if test='status != null and status != ""'>and certification_status=#{status}</if>
            <if test='keyword != null and keyword != ""'>
                and (cast(user_id as char)=#{keyword} or license_no_mask like concat('%',#{keyword},'%'))
            </if>
            order by submitted_at desc
            limit #{offset},#{size}
            </script>
            """)
    List<UserQueryDTO> pageCertifications(@Param("status") String status, @Param("keyword") String keyword,
                                          @Param("offset") int offset, @Param("size") int size);

    /** 统计后台驾驶证认证列表。 */
    @Select("""
            <script>
            select count(*) from user_driving_license_certification
            where deleted=0
            <if test='status != null and status != ""'>and certification_status=#{status}</if>
            <if test='keyword != null and keyword != ""'>
                and (cast(user_id as char)=#{keyword} or license_no_mask like concat('%',#{keyword},'%'))
            </if>
            </script>
            """)
    long countCertifications(@Param("status") String status, @Param("keyword") String keyword);

    /** 查询后台驾驶证认证详情。 */
    @Select("""
            select id,user_id userId,holder_name_cipher holderNameCipher,license_no_cipher licenseNoCipher,
                   license_no_mask licenseNoMask,vehicle_class vehicleClass,first_issue_date firstIssueDate,
                   valid_from validFrom,valid_to validTo,issuing_authority issuingAuthority,
                   license_front_image_key licenseFrontImageKey,license_back_image_key licenseBackImageKey,
                   recognition_source recognitionSource,certification_status certificationStatus,
                   reject_reason rejectReason,reviewer_id reviewerId,submitted_at submittedAt,reviewed_at reviewedAt
            from user_driving_license_certification where id=#{certificationId} and deleted=0 limit 1
            """)
    UserQueryDTO findCertificationById(@Param("certificationId") Long certificationId);

    /** 人工审核驾驶证申请，仅允许更新待审核记录。 */
    @Update("""
            update user_driving_license_certification
            set certification_status=#{auditResult},reject_reason=#{rejectReason},reviewer_id=#{operatorId},
                reviewed_at=#{now},updated_at=#{now}
            where id=#{certificationId} and certification_status='PENDING' and deleted=0
            """)
    int updateCertificationAudit(@Param("certificationId") Long certificationId,
                                 @Param("auditResult") String auditResult,
                                 @Param("rejectReason") String rejectReason,
                                 @Param("operatorId") Long operatorId,
                                 @Param("now") LocalDateTime now);

    /**
     * 查询用户隐私设置。
     */
    @Select("""
            select user_id userId,profile_visibility profileVisibility,vehicle_visibility vehicleVisibility,
                   invite_enabled_flag inviteEnabledFlag from user_privacy_setting where user_id=#{userId} and deleted=0 limit 1
            """)
    UserQueryDTO findPrivacy(@Param("userId") Long userId);

    /**
     * 创建用户默认隐私设置。
     *
     * <p>默认公开个人主页和车辆信息，并允许邀请相关能力。</p>
     */
    @Insert("""
            insert into user_privacy_setting(id,user_id,profile_visibility,vehicle_visibility,invite_enabled_flag,created_at,updated_at,deleted)
            values(#{id},#{userId},'PUBLIC','PUBLIC',1,#{now},#{now},0)
            """)
    int insertPrivacy(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
