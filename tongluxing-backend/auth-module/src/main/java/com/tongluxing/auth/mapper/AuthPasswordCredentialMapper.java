package com.tongluxing.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.auth.entity.AuthPasswordCredential;

/**
 * 密码凭证 MyBatis Mapper。
 *
 * <p>只读写哈希后的密码凭证。明文密码必须在进入 Mapper 前由
 * {@code PasswordEncoder} 转换，禁止作为 SQL 参数或日志字段传入。</p>
 */
@Mapper
public interface AuthPasswordCredentialMapper {

    /**
     * 按用户查询未删除密码凭证。
     *
     * @param userId 全局业务用户 ID
     * @return 密码凭证；尚未设置密码时返回 null
     */
    @Select("""
            select id, user_id, password_hash, password_version, password_status,
                   last_set_time, created_at, updated_at, deleted
            from auth_password_credential
            where user_id = #{userId} and deleted = 0
            limit 1
            """)
    AuthPasswordCredential findByUserId(@Param("userId") Long userId);

    /**
     * 创建用户的第一份密码凭证。
     *
     * @param credential 包含 BCrypt 哈希、算法版本、状态和审计时间的实体
     * @return 数据库影响行数，正常为 1
     */
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
