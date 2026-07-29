package com.tongluxing.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.auth.entity.AuthAccount;

/**
 * 认证账号表 Mapper。
 *
 * <p>负责 auth_account 表的基础查询、新增和最近登录信息更新。</p>
 */
@Mapper
public interface AuthAccountMapper {

    /**
     * 根据手机号查询未删除账号。
     *
     * @param phone 完整登录手机号
     * @return 认证账号；首次登录尚未建号时返回 null
     */
    @Select("""
            select id, user_id, phone, account_status, mini_invite_onboarding_completed, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_account
            where phone = #{phone} and deleted = 0
            limit 1
            """)
    AuthAccount findByPhone(@Param("phone") String phone);

    /**
     * 根据业务用户 ID 查询未删除账号。
     *
     * @param userId 跨模块业务用户 ID
     * @return 认证账号；账号不存在或已逻辑删除时返回 null
     */
    @Select("""
            select id, user_id, phone, account_status, mini_invite_onboarding_completed, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_account
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    AuthAccount findByUserId(@Param("userId") Long userId);

    /**
     * 新增认证账号，数据库写入正常状态、引导未完成和未删除默认值。
     *
     * @param account 服务层生成主键、userId、手机号和审计时间的实体
     * @return 数据库影响行数，正常为 1
     */
    @Insert("""
            insert into auth_account
                (id, user_id, phone, account_status, mini_invite_onboarding_completed, last_login_time, last_login_ip, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{phone}, 1, 0, #{lastLoginTime}, #{lastLoginIp}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(AuthAccount account);

    /**
     * 幂等标记账号已经进入过小程序邀请码引导页。
     *
     * @return 数据库影响行数；重复调用仍保持完成状态
     */
    @Update("""
            update auth_account
            set mini_invite_onboarding_completed = 1, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    int completeMiniInviteOnboarding(
            @Param("userId") Long userId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 登录成功后刷新最近登录时间、IP 和更新时间。
     *
     * @param userId 登录用户 ID
     * @param lastLoginTime 本次成功登录时间
     * @param lastLoginIp 从当前请求解析出的客户端 IP
     * @param updatedAt 账号记录审计更新时间
     * @return 数据库影响行数
     */
    @Update("""
            update auth_account
            set last_login_time = #{lastLoginTime}, last_login_ip = #{lastLoginIp}, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    int updateLastLogin(
            @Param("userId") Long userId,
            @Param("lastLoginTime") LocalDateTime lastLoginTime,
            @Param("lastLoginIp") String lastLoginIp,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
