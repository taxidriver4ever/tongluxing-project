package com.tongdao.user.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.user.entity.UserProfile;

@Mapper
public interface UserProfileMapper {

    @Select("""
            select id, user_id, nickname, avatar_url, gender, birthday, city_code, city_name, bio,
                   profile_completion, real_name_status, created_at, updated_at, deleted
            from user_profile
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    UserProfile findByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into user_profile
                (id, user_id, nickname, avatar_url, gender, birthday, city_code, city_name, bio,
                 profile_completion, real_name_status, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{nickname}, #{avatarUrl}, #{gender}, #{birthday}, #{cityCode}, #{cityName}, #{bio},
                 #{profileCompletion}, #{realNameStatus}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(UserProfile profile);

    @Update("""
            update user_profile
            set nickname = #{nickname},
                avatar_url = #{avatarUrl},
                gender = #{gender},
                birthday = #{birthday},
                city_code = #{cityCode},
                city_name = #{cityName},
                bio = #{bio},
                profile_completion = #{profileCompletion},
                updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    int updateProfile(UserProfile profile);

    @Update("""
            update user_profile
            set real_name_status = #{status}, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    int updateRealNameStatus(@Param("userId") Long userId, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);
}
