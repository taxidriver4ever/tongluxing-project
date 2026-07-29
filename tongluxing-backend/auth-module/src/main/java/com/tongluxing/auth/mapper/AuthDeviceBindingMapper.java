package com.tongluxing.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.auth.entity.AuthDeviceBinding;

/**
 * 设备绑定 MyBatis Mapper。
 *
 * <p>以“用户 + 客户端类型 + 设备标识”定位一台逻辑设备。Mapper 只维护审计快照，
 * 不决定账号能否并发登录；账号级单点登录由 {@code TokenStore} 的 Redis 会话索引控制。</p>
 */
@Mapper
public interface AuthDeviceBindingMapper {

    /**
     * 查询一条未删除的设备绑定。
     *
     * @param userId 业务用户 ID
     * @param clientType 客户端类型
     * @param deviceId 稳定设备标识
     * @return 已有绑定；不存在时返回 null
     */
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

    /**
     * 首次看到该设备时新增绑定记录。
     *
     * @param binding 已由服务层填充主键、状态、登录信息和审计时间的实体
     * @return 数据库影响行数，正常为 1
     */
    @Insert("""
            insert into auth_device_binding
                (id, user_id, phone, client_type, device_id, device_name, platform,
                 bind_status, last_login_time, last_login_ip, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{phone}, #{clientType}, #{deviceId}, #{deviceName}, #{platform},
                 #{bindStatus}, #{lastLoginTime}, #{lastLoginIp}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(AuthDeviceBinding binding);

    /**
     * 已有设备再次登录时刷新设备描述、状态和最近登录信息。
     *
     * <p>更新条件包含未删除标记，避免意外恢复已逻辑删除的旧绑定。</p>
     *
     * @return 数据库影响行数；0 表示目标绑定不存在或已经删除
     */
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
