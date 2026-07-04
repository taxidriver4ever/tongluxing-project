package com.tongluxing.user.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.tongluxing.user.dto.UserQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户域数据库访问接口。
 *
 * <p>集中管理用户资料、实名认证记录和隐私设置的读写操作。
 * 该 Mapper 只负责数据访问，业务上的缓存、加密、权限判断由 Service 层处理。</p>
 */
@Mapper
public interface UserDomainMapper {

    /**
     * 查询用户资料，并附带最新实名认证状态。
     */
    @Select("""
            select p.id, p.user_id userId, p.nickname, p.avatar_image_key avatarImageKey, p.gender, p.birthday,
                   p.city_code cityCode, p.city_name cityName, p.bio, p.profile_status profileStatus,
                   coalesce((select c.certification_status from user_identity_certification c
                             where c.user_id=p.user_id and c.deleted=0 order by c.submitted_at desc limit 1), 'UNSUBMITTED') certificationStatus
            from user_profile p where p.user_id=#{userId} and p.deleted=0 limit 1
            """)
    UserQueryDTO findProfile(@Param("userId") Long userId);

    /**
     * 创建用户默认资料。
     *
     * <p>默认资料在用户首次访问用户模块能力时懒初始化。</p>
     */
    @Insert("""
            insert into user_profile(id,user_id,nickname,avatar_image_key,gender,birthday,city_code,city_name,bio,profile_status,created_at,updated_at,deleted)
            values(#{id},#{userId},'', '',0,null,'','', '', 'ACTIVE',#{now},#{now},0)
            """)
    int insertProfile(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

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
     * 查询用户最新一条实名认证记录。
     */
    @Select("""
            select id,user_id userId,certification_status certificationStatus,reject_reason rejectReason,
                   submitted_at submittedAt,reviewed_at reviewedAt
            from user_identity_certification where user_id=#{userId} and deleted=0 order by submitted_at desc limit 1
            """)
    UserQueryDTO findLatestCertification(@Param("userId") Long userId);

    /**
     * 新增实名认证申请记录。
     *
     * <p>真实姓名和证件号在进入 Mapper 前已完成加密，这里只保存密文。</p>
     */
    @Insert("""
            insert into user_identity_certification(id,user_id,real_name_cipher,id_card_no_cipher,driving_license_image_key,
                face_image_key,certification_status,reject_reason,submitted_at,reviewed_at,created_at,updated_at,deleted)
            values(#{id},#{userId},#{realNameCipher},#{idCardCipher},#{licenseKey},#{faceKey},'PENDING','',#{now},#{now},#{now},#{now},0)
            """)
    int insertCertification(@Param("id") Long id, @Param("userId") Long userId,
                            @Param("realNameCipher") String realNameCipher, @Param("idCardCipher") String idCardCipher,
                            @Param("licenseKey") String licenseKey, @Param("faceKey") String faceKey,
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
