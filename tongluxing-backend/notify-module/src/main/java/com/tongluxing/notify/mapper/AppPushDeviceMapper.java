package com.tongluxing.notify.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.notify.entity.AppPushDevice;

/** App 系统推送设备 Mapper。 */
@Mapper
public interface AppPushDeviceMapper {

    @Insert("""
            insert into app_push_device
                (id, user_id, device_id, platform, vendor, push_token, app_version,
                 enabled, last_seen_at, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{deviceId}, #{platform}, #{vendor}, #{pushToken}, #{appVersion},
                 1, #{lastSeenAt}, #{createdAt}, #{updatedAt}, 0)
            on duplicate key update
                platform = values(platform), vendor = values(vendor), push_token = values(push_token),
                app_version = values(app_version), enabled = 1, last_seen_at = values(last_seen_at),
                updated_at = values(updated_at)
            """)
    void upsert(AppPushDevice device);

    @Select("""
            select id, user_id, device_id, platform, vendor, push_token, app_version,
                   enabled, last_seen_at, created_at, updated_at, deleted
            from app_push_device
            where user_id = #{userId} and enabled = 1 and deleted = 0
            order by updated_at desc
            """)
    List<AppPushDevice> findEnabledByUserId(@Param("userId") Long userId);

    @Update("""
            update app_push_device
            set enabled = 0, updated_at = #{now}
            where user_id = #{userId} and device_id = #{deviceId} and deleted = 0
            """)
    int disable(@Param("userId") Long userId, @Param("deviceId") String deviceId,
                @Param("now") LocalDateTime now);
}
