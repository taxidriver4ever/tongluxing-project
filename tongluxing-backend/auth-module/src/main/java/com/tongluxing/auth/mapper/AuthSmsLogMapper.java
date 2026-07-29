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

    /**
     * 写入一条短信验证码发送记录。
     *
     * <p>日志不保存验证码正文，只记录手机号、场景、供应商和发送结果。
     * 即使当前使用 mock 通道，也保留相同审计结构以便后续无缝替换真实供应商。</p>
     *
     * @param id 雪花日志主键
     * @param phone 接收手机号
     * @param scene 验证码业务场景
     * @param sendStatus 1 成功，2 失败
     * @param provider 短信供应商标识，当前为 mock
     * @param errorMessage 失败内部原因，成功时为空
     * @return 数据库影响行数
     */
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
