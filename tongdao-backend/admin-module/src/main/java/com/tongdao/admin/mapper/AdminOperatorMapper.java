package com.tongdao.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.admin.entity.AdminOperator;

@Mapper
public interface AdminOperatorMapper {

    @Select("""
            select id, username, display_name, phone, password_hash, operator_status,
                   last_login_at, created_at, updated_at, deleted
            from admin_operator
            where id = #{id}
              and deleted = 0
            limit 1
            """)
    AdminOperator findById(@Param("id") Long id);

    @Select("""
            select id, username, display_name, phone, password_hash, operator_status,
                   last_login_at, created_at, updated_at, deleted
            from admin_operator
            where username = #{username}
              and deleted = 0
            limit 1
            """)
    AdminOperator findByUsername(@Param("username") String username);
}
