package com.tongdao.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.auth.entity.AuthAccount;

@Mapper
public interface AuthAccountMapper {

    @Select("""
            select id, user_id, phone, account_status, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_account
            where phone = #{phone} and deleted = 0
            limit 1
            """)
    AuthAccount findByPhone(@Param("phone") String phone);

    @Select("""
            select id, user_id, phone, account_status, last_login_time, last_login_ip, created_at, updated_at, deleted
            from auth_account
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    AuthAccount findByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into auth_account
                (id, user_id, phone, account_status, last_login_time, last_login_ip, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{phone}, 1, #{lastLoginTime}, #{lastLoginIp}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(AuthAccount account);

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
