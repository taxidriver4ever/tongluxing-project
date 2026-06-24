package com.tongdao.invite.integration;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 邀请模块访问认证账号信息的端口。
 *
 * <p>邀请模块不能直接访问 auth-module 私有 Mapper。需要注册时间或手机号解析用户 ID 时，
 * 由 auth-module 提供适配器实现该端口。</p>
 */
public interface InviteAuthPort {

    /**
     * 查询用户注册时间。
     *
     * @param userId 用户 ID
     * @return 注册时间；查不到时返回空
     */
    Optional<LocalDateTime> findRegisteredAt(Long userId);

    /**
     * 根据手机号查询可作为邀请人的用户 ID。
     *
     * @param phone 邀请人手机号
     * @return 可用邀请人用户 ID；手机号不存在或账号不可用时返回空
     */
    Optional<Long> findAvailableUserIdByPhone(String phone);
}
