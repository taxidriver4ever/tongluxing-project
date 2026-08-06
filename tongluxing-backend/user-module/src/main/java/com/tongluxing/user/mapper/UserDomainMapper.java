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
     *
     * <p>用户统计采用左连接并通过 {@code coalesce} 归零；认证状态按提交时间倒序取
     * 最近一条，未认证用户统一返回 UNSUBMITTED。</p>
     *
     * @param userId 要查询的平台用户 ID
     * @return 聚合查询结果；资料不存在或已逻辑删除时返回 {@code null}
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

    /**
     * 搜索允许公开展示的用户，供“同路人”标签分页展示。
     *
     * <p>只选择 ACTIVE 且 profile_visibility=PUBLIC 的用户，并排除当前登录用户。
     * 普通关键词可模糊匹配昵称、城市、简介或精确用户 ID；识别为完整手机号时仅执行
     * 手机号精确匹配，以 TLX 开头时仅执行同路行号精确匹配。手机号只参与匹配，不会
     * 包含在接口响应中。空关键词表示浏览全部公开用户。结果优先按累计行程数排序。</p>
     */
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
            left join auth_account account on account.user_id=p.user_id
                 and account.deleted=0 and account.account_status=1
            left join user_statistics s on s.user_id=p.user_id
            where p.deleted=0 and p.profile_status='ACTIVE' and p.user_id &lt;&gt; #{excludeUserId}
            <if test='exactPhoneSearch'>
              and account.phone=#{keyword}
            </if>
            <if test='tongluxingIdSearch'>
              and lower(p.tongluxing_id)=lower(#{keyword})
            </if>
            <if test='!exactPhoneSearch and !tongluxingIdSearch and keyword != null and keyword != ""'>
              and (lower(p.nickname) like concat('%',lower(#{keyword}),'%')
                   or lower(p.city_name) like concat('%',lower(#{keyword}),'%')
                   or lower(p.bio) like concat('%',lower(#{keyword}),'%')
                   or cast(p.user_id as char)=#{keyword})
            </if>
            order by coalesce(s.total_trip_count,0) desc, p.updated_at desc
            limit #{offset},#{size}
            </script>
            """)
    List<UserQueryDTO> searchPublicProfiles(@Param("keyword") String keyword,
                                            @Param("exactPhoneSearch") boolean exactPhoneSearch,
                                            @Param("tongluxingIdSearch") boolean tongluxingIdSearch,
                                            @Param("excludeUserId") Long excludeUserId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    /**
     * 创建用户默认资料。
     *
     * <p>默认资料在用户首次访问用户模块能力时懒初始化。</p>
     *
     * @return 成功插入的行数；并发初始化可能由唯一索引抛出重复键异常
     */
    @Insert("""
            insert into user_profile(id,user_id,tongluxing_id,nickname,avatar_image_key,gender,birthday,city_code,city_name,bio,profile_status,created_at,updated_at,deleted)
            values(#{id},#{userId},#{tongluxingId},#{tongluxingId}, '',0,null,'','', '', 'ACTIVE',#{now},#{now},0)
            """)
    int insertProfile(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("tongluxingId") String tongluxingId, @Param("now") LocalDateTime now);

    /**
     * 为迁移前的历史资料补齐不可变的同路行号。
     *
     * <p>WHERE 条件只允许更新空值，避免并发请求覆盖已经确定的公开号码。</p>
     */
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
     *
     * <p>Service 已把增量请求与旧值合并，因此 SQL 对全部可编辑列执行一次完整更新。</p>
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
     *
     * @return 按提交时间倒序的第一条记录；从未提交时返回 {@code null}
     */
    @Select("""
            select id,user_id userId,certification_status certificationStatus,reject_reason rejectReason,
                   submitted_at submittedAt,reviewed_at reviewedAt
            from user_driving_license_certification where user_id=#{userId} and deleted=0 order by submitted_at desc limit 1
            """)
    UserQueryDTO findLatestCertification(@Param("userId") Long userId);

    /**
     * 新增一条系统自动通过的驾驶证认证记录。
     *
     * <p>姓名和证件号参数必须已经由 Service 加密。只有经过请求校验、日期校验和
     * 正反面材料完整性校验后才会调用本方法；状态由 SQL 固定写为 APPROVED，
     * 客户端无法伪造认证结果。</p>
     */
    @Insert("""
            insert into user_driving_license_certification(
                id,user_id,holder_name_cipher,license_no_cipher,license_no_mask,vehicle_class,
                first_issue_date,valid_from,valid_to,issuing_authority,license_front_image_key,
                license_back_image_key,recognition_source,certification_status,reject_reason,
                reviewer_id,submitted_at,reviewed_at,created_at,updated_at,deleted)
            values(#{id},#{userId},#{holderNameCipher},#{licenseNoCipher},#{licenseNoMask},#{vehicleClass},
                #{firstIssueDate},#{validFrom},#{validTo},#{issuingAuthority},#{licenseFrontImageKey},
                #{licenseBackImageKey},#{recognitionSource},'APPROVED',null,null,#{now},#{now},#{now},#{now},0)
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

    /**
     * 分页查询后台驾驶证认证列表。
     *
     * <p>列表只读取姓名密文和证件号脱敏值，不读取完整证件号密文；状态与关键词为空
     * 时不添加相应条件，结果按提交时间倒序。</p>
     */
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

    /** 使用与分页列表完全相同的过滤条件统计总记录数，供后台构造分页结果。 */
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

    /**
     * 查询后台驾驶证认证详情。
     *
     * <p>该查询包含姓名和证件号密文，只能由已授权的审核业务调用，并由 Service
     * 解密后转换为审核专用 VO。</p>
     */
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

    /**
     * 原子应用人工审核结果。
     *
     * <p>WHERE 条件要求当前仍为 PENDING，相当于一次乐观并发控制：两个审核员同时
     * 操作时只有第一个更新成功，后一个收到影响行数 0 并由 Service 提示刷新。</p>
     */
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
     *
     * @return 用户的全部隐私开关；尚未懒初始化时返回 {@code null}
     */
    @Select("""
            select user_id userId,profile_visibility profileVisibility,vehicle_visibility vehicleVisibility,
                   invite_enabled_flag inviteEnabledFlag,city_visible_flag cityVisibleFlag,
                   bio_visible_flag bioVisibleFlag,trip_stats_visible_flag tripStatsVisibleFlag,
                   level_visible_flag levelVisibleFlag,location_enabled_flag locationEnabledFlag,
                   notification_enabled_flag notificationEnabledFlag
            from user_privacy_setting where user_id=#{userId} and deleted=0 limit 1
            """)
    UserQueryDTO findPrivacy(@Param("userId") Long userId);

    /**
     * 创建用户默认隐私设置。
     *
     * <p>默认公开个人主页和车辆信息，并允许邀请相关能力。</p>
     * <p>user_id 与 deleted 的唯一索引负责阻止并发重复初始化。</p>
     */
    @Insert("""
            insert into user_privacy_setting(id,user_id,profile_visibility,vehicle_visibility,invite_enabled_flag,
                city_visible_flag,bio_visible_flag,trip_stats_visible_flag,level_visible_flag,
                location_enabled_flag,notification_enabled_flag,created_at,updated_at,deleted)
            values(#{id},#{userId},'PUBLIC','PUBLIC',1,1,1,1,1,1,1,#{now},#{now},0)
            """)
    int insertPrivacy(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 完整更新一名用户的隐私设置。
     *
     * <p>Service 会先读取旧记录并合并请求中的非空字段，因此传入对象包含每一个
     * 开关的最终值；本方法不负责解释“未传字段”的语义。</p>
     *
     * @param value 包含用户 ID、全部最终开关值和更新时间的查询 DTO
     * @return 实际更新的行数
     */
    @Update("""
            update user_privacy_setting
            set profile_visibility=#{profileVisibility},vehicle_visibility=#{vehicleVisibility},
                invite_enabled_flag=#{inviteEnabledFlag},city_visible_flag=#{cityVisibleFlag},
                bio_visible_flag=#{bioVisibleFlag},trip_stats_visible_flag=#{tripStatsVisibleFlag},
                level_visible_flag=#{levelVisibleFlag},location_enabled_flag=#{locationEnabledFlag},
                notification_enabled_flag=#{notificationEnabledFlag},updated_at=#{updatedAt}
            where user_id=#{userId} and deleted=0
            """)
    int updatePrivacy(UserQueryDTO value);
}
