package com.tongdao.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.admin.entity.AdminRole;

@Mapper
public interface AdminRoleMapper {

    @Select("""
            select r.id, r.role_code, r.role_name, r.permission_json, r.role_status,
                   r.created_at, r.updated_at, r.deleted
            from admin_role r
            inner join admin_operator_role rel on rel.role_id = r.id
            where rel.operator_id = #{operatorId}
              and rel.deleted = 0
              and r.deleted = 0
              and r.role_status = 'ACTIVE'
            order by r.role_code asc
            """)
    List<AdminRole> findByOperatorId(@Param("operatorId") Long operatorId);
}
