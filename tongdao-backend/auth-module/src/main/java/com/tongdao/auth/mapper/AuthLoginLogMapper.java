package com.tongdao.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthLoginLogMapper {

    @Insert("""
            insert into auth_login_log (id, user_id, phone, action_type, device_id, ip, success, message, created_at)
            values (#{id}, #{userId}, #{phone}, #{actionType}, #{deviceId}, #{ip}, #{success}, #{message}, now())
            """)
    int insert(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("phone") String phone,
            @Param("actionType") String actionType,
            @Param("deviceId") String deviceId,
            @Param("ip") String ip,
            @Param("success") Integer success,
            @Param("message") String message
    );
}
