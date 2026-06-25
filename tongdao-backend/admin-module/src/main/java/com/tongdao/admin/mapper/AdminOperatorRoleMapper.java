package com.tongdao.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.admin.entity.AdminOperatorRole;

@Mapper
public interface AdminOperatorRoleMapper {

    @Select("""
            select id, operator_id, role_id, created_at, deleted
            from admin_operator_role
            where operator_id = #{operatorId}
              and deleted = 0
            order by created_at desc
            """)
    List<AdminOperatorRole> findByOperatorId(@Param("operatorId") Long operatorId);
}
