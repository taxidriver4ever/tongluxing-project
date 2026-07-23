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

    /** 根据手机号查询未删除账号。 */
    @Select("""
            select id, user_id, phone, account_status, mini_invite_onboarding_completed, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_account
            where phone = #{phone} and deleted = 0
            limit 1
            """)
    AuthAccount findByPhone(@Param("phone") String phone);

    /** 根据业务用户 ID 查询未删除账号。 */
    @Select("""
            select id, user_id, phone, account_status, mini_invite_onboarding_completed, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_account
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    AuthAccount findByUserId(@Param("userId") Long userId);

    /** 新增认证账号，默认状态为正常、未删除。 */
    @Insert("""
            insert into auth_account
                (id, user_id, phone, account_status, mini_invite_onboarding_completed, last_login_time, last_login_ip, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{phone}, 1, 0, #{lastLoginTime}, #{lastLoginIp}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(AuthAccount account);

    /** 标记当前账号已经进入过小程序邀请码引导页。 */
    @Update("""
            update auth_account
            set mini_invite_onboarding_completed = 1, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    int completeMiniInviteOnboarding(
            @Param("userId") Long userId,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /** 登录成功后刷新最近登录时间、IP 和更新时间。 */
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
