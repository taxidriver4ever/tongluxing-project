package com.tongdao.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 登录行为日志 Mapper。
 *
 * <p>记录登录、退出、刷新令牌等认证行为，便于审计和排查问题。</p>
 */
@Mapper
public interface AuthLoginLogMapper {

    /** 写入一条认证行为日志。 */
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
