package com.tongluxing.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 短信发送日志 Mapper。
 *
 * <p>记录验证码发送结果，便于统计发送量和定位短信通道问题。</p>
 */
@Mapper
public interface AuthSmsLogMapper {

    /** 写入一条短信验证码发送记录。 */
    @Insert("""
            insert into auth_sms_log (id, phone, scene, send_status, provider, error_message, created_at)
            values (#{id}, #{phone}, #{scene}, #{sendStatus}, #{provider}, #{errorMessage}, now())
            """)
    int insert(
            @Param("id") Long id,
            @Param("phone") String phone,
            @Param("scene") String scene,
            @Param("sendStatus") Integer sendStatus,
            @Param("provider") String provider,
            @Param("errorMessage") String errorMessage
    );
}
