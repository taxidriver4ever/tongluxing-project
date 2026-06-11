package com.tongdao.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthSmsLogMapper {

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
