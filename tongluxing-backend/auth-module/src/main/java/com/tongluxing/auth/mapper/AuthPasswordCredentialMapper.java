package com.tongluxing.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.auth.entity.AuthPasswordCredential;

/**
 * 密码凭证 Mapper。
 */
@Mapper
public interface AuthPasswordCredentialMapper {

    @Select("""
            select id, user_id, password_hash, password_version, password_status,
                   last_set_time, created_at, updated_at, deleted
            from auth_password_credential
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    AuthPasswordCredential findByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into auth_password_credential
                (id, user_id, password_hash, password_version, password_status,
                 last_set_time, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{passwordHash}, #{passwordVersion}, #{passwordStatus},
                 #{lastSetTime}, #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(AuthPasswordCredential credential);
}
