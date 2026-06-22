package com.tongdao.user.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.tongdao.user.dto.UserQueryDTO;

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
    UserQueryDTO findProfile(@Param("userId") Long userId);

    @Insert("""
            insert into user_profile(id,user_id,nickname,avatar_image_key,gender,birthday,city_code,city_name,bio,profile_status,created_at,updated_at,deleted)
            values(#{id},#{userId},'', '',0,null,'','', '', 'ACTIVE',#{now},#{now},0)
            """)
    int insertProfile(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

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

    @Select("""
            select id,user_id userId,certification_status certificationStatus,reject_reason rejectReason,
                   submitted_at submittedAt,reviewed_at reviewedAt
            from user_identity_certification where user_id=#{userId} and deleted=0 order by submitted_at desc limit 1
            """)
    UserQueryDTO findLatestCertification(@Param("userId") Long userId);

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
    UserQueryDTO findPrivacy(@Param("userId") Long userId);

    @Insert("""
            insert into user_privacy_setting(id,user_id,profile_visibility,vehicle_visibility,invite_enabled_flag,created_at,updated_at,deleted)
            values(#{id},#{userId},'PUBLIC','PUBLIC',1,#{now},#{now},0)
            """)
    int insertPrivacy(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
