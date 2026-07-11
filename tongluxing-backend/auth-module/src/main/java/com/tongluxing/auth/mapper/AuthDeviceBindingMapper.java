package com.tongluxing.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.auth.entity.AuthDeviceBinding;

/**
 * 设备绑定 Mapper。
 */
@Mapper
public interface AuthDeviceBindingMapper {

    @Select("""
            select id, user_id, phone, client_type, device_id, device_name, platform,
                   bind_status, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_device_binding
            where user_id = #{userId}
              and client_type = #{clientType}
              and device_id = #{deviceId}
              and deleted = 0
            limit 1
            """)
    AuthDeviceBinding find(@Param("userId") Long userId, @Param("clientType") String clientType, @Param("deviceId") String deviceId);

    @Insert("""
            insert into auth_device_binding
                (id, user_id, phone, client_type, device_id, device_name, platform,
                 bind_status, last_login_time, last_login_ip, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{phone}, #{clientType}, #{deviceId}, #{deviceName}, #{platform},
                 #{bindStatus}, #{lastLoginTime}, #{lastLoginIp}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(AuthDeviceBinding binding);

    @Update("""
            update auth_device_binding
            set phone = #{phone},
                device_name = #{deviceName},
                platform = #{platform},
                bind_status = 1,
                last_login_time = #{lastLoginTime},
                last_login_ip = #{lastLoginIp},
                updated_at = #{updatedAt}
            where user_id = #{userId}
              and client_type = #{clientType}
              and device_id = #{deviceId}
              and deleted = 0
            """)
    int updateLogin(
            @Param("userId") Long userId,
            @Param("phone") String phone,
            @Param("clientType") String clientType,
            @Param("deviceId") String deviceId,
            @Param("deviceName") String deviceName,
            @Param("platform") String platform,
            @Param("lastLoginTime") LocalDateTime lastLoginTime,
            @Param("lastLoginIp") String lastLoginIp,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
