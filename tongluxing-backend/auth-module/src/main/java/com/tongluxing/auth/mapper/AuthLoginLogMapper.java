package com.tongluxing.auth.mapper;

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

    /**
     * 写入一条认证行为日志。
     *
     * <p>失败日志允许 userId 为空，例如手机号尚未注册或验证码在识别账号前失败。
     * message 保存服务端内部审计原因，不直接作为客户端错误提示。</p>
     *
     * @param id 雪花日志主键
     * @param userId 已识别的业务用户 ID，未知时为空
     * @param phone 尝试登录的手机号
     * @param actionType login、password_login、refresh、logout 等稳定动作代码
     * @param deviceId 客户端设备标识
     * @param ip 客户端 IP
     * @param success 1 成功，0 失败
     * @param message 审计说明，不得包含密码、验证码或完整 Token
     * @return 数据库影响行数
     */
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
