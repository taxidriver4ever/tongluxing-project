package com.tongdao.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.user.entity.UserPrivacySetting;

@Mapper
public interface UserPrivacySettingMapper {

    @Select("""
            select id, user_id, profile_visible, phone_visible, trip_visible, location_visible,
                   allow_team_invite, allow_private_message, created_at, updated_at
            from user_privacy_setting
            where user_id = #{userId}
            limit 1
            """)
    UserPrivacySetting findByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into user_privacy_setting
                (id, user_id, profile_visible, phone_visible, trip_visible, location_visible,
                 allow_team_invite, allow_private_message, created_at, updated_at)
            values
                (#{id}, #{userId}, #{profileVisible}, #{phoneVisible}, #{tripVisible}, #{locationVisible},
                 #{allowTeamInvite}, #{allowPrivateMessage}, #{createdAt}, #{updatedAt})
            """)
    int insert(UserPrivacySetting setting);

    @Update("""
            update user_privacy_setting
            set profile_visible = #{profileVisible},
                phone_visible = #{phoneVisible},
                trip_visible = #{tripVisible},
                location_visible = #{locationVisible},
                allow_team_invite = #{allowTeamInvite},
                allow_private_message = #{allowPrivateMessage},
                updated_at = #{updatedAt}
            where user_id = #{userId}
            """)
    int update(UserPrivacySetting setting);
}
